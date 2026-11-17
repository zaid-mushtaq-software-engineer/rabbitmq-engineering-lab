package com.example.notificationlab.consumer.service;

import com.example.notificationlab.contracts.NotificationChannel;
import com.example.notificationlab.contracts.NotificationMessage;
import com.example.notificationlab.contracts.RabbitNames;
import com.example.notificationlab.consumer.persistence.NotificationAttemptRepository;
import com.example.notificationlab.consumer.persistence.NotificationRepository;
import com.example.notificationlab.consumer.persistence.NotificationStatus;
import com.example.notificationlab.consumer.persistence.ProcessedMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@Testcontainers
class ConsumerConcurrencyIntegrationTest {

    private static final int MESSAGE_COUNT = 50;

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
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
        registry.add("lab.simulation.latency-min", () -> "0ms");
        registry.add("lab.simulation.latency-max", () -> "0ms");
        registry.add("lab.consumer.email.concurrency", () -> "4");
        registry.add("lab.consumer.email.max-concurrency", () -> "4");
        registry.add("lab.consumer.email.prefetch", () -> "10");
        registry.add("lab.consumer.sms.concurrency", () -> "4");
        registry.add("lab.consumer.sms.max-concurrency", () -> "4");
        registry.add("lab.consumer.sms.prefetch", () -> "10");
        registry.add("lab.consumer.sms.listener-enabled", () -> "true");
    }

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RabbitAdmin rabbitAdmin;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationAttemptRepository attemptRepository;

    @Autowired
    private ProcessedMessageRepository processedMessageRepository;

    @BeforeEach
    void purgeQueuesAndDatabase() {
        rabbitAdmin.purgeQueue(RabbitNames.EMAIL_QUEUE, false);
        rabbitAdmin.purgeQueue(RabbitNames.SMS_QUEUE, false);
        processedMessageRepository.deleteAll();
        attemptRepository.deleteAll();
        notificationRepository.deleteAll();
    }

    @RepeatedTest(3)
    void fiftyMessagesAllSentWithIdempotencyRows() {
        List<NotificationMessage> published = new ArrayList<>(MESSAGE_COUNT);
        for (int i = 0; i < MESSAGE_COUNT / 2; i++) {
            NotificationMessage email = message(NotificationChannel.EMAIL, "user" + i + "@example.com");
            NotificationMessage sms = message(NotificationChannel.SMS, "+1555020" + String.format("%04d", i));
            rabbitTemplate.convertAndSend(RabbitNames.DIRECT_EXCHANGE, RabbitNames.ROUTING_EMAIL, email);
            rabbitTemplate.convertAndSend(RabbitNames.DIRECT_EXCHANGE, RabbitNames.ROUTING_SMS, sms);
            published.add(email);
            published.add(sms);
        }

        await().atMost(java.time.Duration.ofSeconds(90)).untilAsserted(() -> {
            for (NotificationMessage msg : published) {
                var row = notificationRepository.findById(msg.getNotificationId());
                assertThat(row).isPresent();
                assertThat(row.get().getStatus()).isEqualTo(NotificationStatus.SENT);
                assertThat(processedMessageRepository.findById(msg.getMessageId())).isPresent();
                assertThat(attemptRepository.findByNotificationIdOrderByAttemptNumberAsc(msg.getNotificationId()))
                        .hasSize(1);
            }
        });
    }

    private static NotificationMessage message(NotificationChannel channel, String recipient) {
        return new NotificationMessage(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                channel,
                recipient,
                "phase-8-concurrency",
                Instant.now(),
                "phase-8",
                0
        );
    }
}