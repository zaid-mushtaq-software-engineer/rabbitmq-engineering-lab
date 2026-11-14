package com.example.notificationlab.consumer.service;

import com.example.notificationlab.contracts.NotificationChannel;
import com.example.notificationlab.contracts.NotificationMessage;
import com.example.notificationlab.contracts.RabbitNames;
import com.example.notificationlab.consumer.persistence.NotificationAttemptRepository;
import com.example.notificationlab.consumer.persistence.NotificationRepository;
import com.example.notificationlab.consumer.persistence.NotificationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@Testcontainers
class NotificationProcessingIntegrationTest {

    @Container
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:4-management");

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("notification_lab")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", RABBIT::getHost);
        registry.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBIT::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBIT::getAdminPassword);
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("lab.simulation.latency-min", () -> "0ms");
        registry.add("lab.simulation.latency-max", () -> "0ms");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
        registry.add("lab.retry.delay", () -> "1s");
        registry.add("lab.retry.max-retries", () -> "3");
    }

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RabbitAdmin rabbitAdmin;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationAttemptRepository attemptRepository;

    @BeforeEach
    void purgeQueues() {
        rabbitAdmin.purgeQueue(RabbitNames.EMAIL_QUEUE, false);
        rabbitAdmin.purgeQueue(RabbitNames.SMS_QUEUE, false);
        rabbitAdmin.purgeQueue(RabbitNames.DEAD_LETTER_QUEUE, false);
        rabbitAdmin.purgeQueue(RabbitNames.EMAIL_RETRY_QUEUE, false);
        rabbitAdmin.purgeQueue(RabbitNames.SMS_RETRY_QUEUE, false);
    }

    @Test
    void publishedMessageEndsAsSent() {
        NotificationMessage payload = message(NotificationChannel.EMAIL, "alice@example.com");
        rabbitTemplate.convertAndSend(RabbitNames.DIRECT_EXCHANGE, RabbitNames.ROUTING_EMAIL, payload);

        await().atMost(java.time.Duration.ofSeconds(30)).untilAsserted(() -> {
            var notification = notificationRepository.findById(payload.getNotificationId());
            assertThat(notification).isPresent();
            assertThat(notification.get().getStatus()).isEqualTo(NotificationStatus.SENT);
            assertThat(notification.get().getAttemptCount()).isEqualTo(1);
        });
    }

    @Test
    void permanentFailureEndsDeadLetteredWithMessageInDlq() {
        NotificationMessage payload = message(NotificationChannel.EMAIL, "perm-fail@example.com");
        rabbitTemplate.convertAndSend(RabbitNames.DIRECT_EXCHANGE, RabbitNames.ROUTING_EMAIL, payload);

        await().atMost(java.time.Duration.ofSeconds(10)).untilAsserted(() -> {
            var record = notificationRepository.findById(payload.getNotificationId());
            assertThat(record).isPresent();
            assertThat(record.get().getStatus()).isEqualTo(NotificationStatus.DEAD_LETTERED);
            assertThat(record.get().getAttemptCount()).isEqualTo(1);
            assertThat(attemptRepository.findByNotificationIdOrderByAttemptNumberAsc(payload.getNotificationId()))
                    .hasSize(1);
            assertThat(queueDepth(RabbitNames.DEAD_LETTER_QUEUE)).isEqualTo(1);
            assertThat(queueDepth(RabbitNames.EMAIL_QUEUE)).isZero();
        });
    }

    @Test
    void smsPermanentFailureDeadLetters() {
        NotificationMessage payload = message(NotificationChannel.SMS, "+15550100003");
        rabbitTemplate.convertAndSend(RabbitNames.DIRECT_EXCHANGE, RabbitNames.ROUTING_SMS, payload);

        await().atMost(java.time.Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(notificationRepository.findById(payload.getNotificationId()))
                    .isPresent()
                    .get()
                    .extracting(e -> e.getStatus())
                    .isEqualTo(NotificationStatus.DEAD_LETTERED);
            assertThat(queueDepth(RabbitNames.DEAD_LETTER_QUEUE)).isEqualTo(1);
        });
    }

    @Test
    void flakyRecipientSucceedsAfterRetries() {
        NotificationMessage payload = message(NotificationChannel.EMAIL, "flaky@example.com");
        rabbitTemplate.convertAndSend(RabbitNames.DIRECT_EXCHANGE, RabbitNames.ROUTING_EMAIL, payload);

        await().atMost(java.time.Duration.ofSeconds(45)).untilAsserted(() -> {
            var record = notificationRepository.findById(payload.getNotificationId());
            assertThat(record).isPresent();
            assertThat(record.get().getStatus()).isEqualTo(NotificationStatus.SENT);
            assertThat(record.get().getRetryCount()).isEqualTo(2);
            assertThat(record.get().getAttemptCount()).isEqualTo(3);
            assertThat(queueDepth(RabbitNames.EMAIL_QUEUE)).isZero();
            assertThat(queueDepth(RabbitNames.EMAIL_RETRY_QUEUE)).isZero();
        });
    }

    @Test
    void tempFailExhaustsRetriesAndDeadLetters() {
        NotificationMessage payload = message(NotificationChannel.EMAIL, "temp-fail@example.com");
        rabbitTemplate.convertAndSend(RabbitNames.DIRECT_EXCHANGE, RabbitNames.ROUTING_EMAIL, payload);

        await().atMost(java.time.Duration.ofSeconds(90)).untilAsserted(() -> {
            var record = notificationRepository.findById(payload.getNotificationId());
            assertThat(record).isPresent();
            assertThat(record.get().getStatus()).isEqualTo(NotificationStatus.DEAD_LETTERED);
            assertThat(record.get().getRetryCount()).isEqualTo(3);
            assertThat(record.get().getAttemptCount()).isEqualTo(4);
            assertThat(record.get().getLastError()).contains("retries exhausted");
            assertThat(queueDepth(RabbitNames.DEAD_LETTER_QUEUE)).isEqualTo(1);
            assertThat(queueDepth(RabbitNames.EMAIL_QUEUE)).isZero();
        });
    }

    @Test
    void malformedJsonDeadLettersWithoutLooping() {
        Message junk = MessageBuilder.withBody("{ not-json".getBytes(StandardCharsets.UTF_8))
                .setContentType("application/json")
                .build();
        rabbitTemplate.send(RabbitNames.EMAIL_QUEUE, junk);

        await().atMost(java.time.Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(queueDepth(RabbitNames.DEAD_LETTER_QUEUE)).isEqualTo(1);
            assertThat(queueDepth(RabbitNames.EMAIL_QUEUE)).isZero();
        });
    }

    @Test
    void channelMismatchDeadLetters() {
        NotificationMessage wrongChannel = message(NotificationChannel.SMS, "alice@example.com");
        rabbitTemplate.convertAndSend(RabbitNames.DIRECT_EXCHANGE, RabbitNames.ROUTING_EMAIL, wrongChannel);

        await().atMost(java.time.Duration.ofSeconds(10)).untilAsserted(() -> {
            var record = notificationRepository.findById(wrongChannel.getNotificationId());
            assertThat(record).isPresent();
            assertThat(record.get().getStatus()).isEqualTo(NotificationStatus.DEAD_LETTERED);
            assertThat(queueDepth(RabbitNames.DEAD_LETTER_QUEUE)).isEqualTo(1);
            assertThat(queueDepth(RabbitNames.EMAIL_QUEUE)).isZero();
        });
    }

    private int queueDepth(String queue) {
        Properties props = rabbitAdmin.getQueueProperties(queue);
        return props == null ? 0 : Integer.parseInt(props.get("QUEUE_MESSAGE_COUNT").toString());
    }

    private static NotificationMessage message(NotificationChannel channel, String recipient) {
        return new NotificationMessage(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                channel,
                recipient,
                "phase-5-test",
                Instant.now(),
                "phase-5-test",
                0
        );
    }
}