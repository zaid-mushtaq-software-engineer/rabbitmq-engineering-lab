package com.example.notificationlab.consumer.messaging;

import com.example.notificationlab.contracts.NotificationChannel;
import com.example.notificationlab.contracts.NotificationMessage;
import com.example.notificationlab.contracts.RabbitNames;
import com.example.notificationlab.consumer.persistence.NotificationRepository;
import com.example.notificationlab.consumer.persistence.NotificationStatus;
import org.junit.jupiter.api.Test;
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
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@Testcontainers
class ManualAckIntegrationTest {

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
        registry.add("lab.simulation.slow-delay", () -> "2s");
    }

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RabbitAdmin rabbitAdmin;

    @Autowired
    private NotificationRepository notificationRepository;

    @Test
    void queueEmptyAfterSuccessfulAck() {
        NotificationMessage payload = message(NotificationChannel.EMAIL, "alice@example.com");

        rabbitTemplate.convertAndSend(RabbitNames.DIRECT_EXCHANGE, RabbitNames.ROUTING_EMAIL, payload);

        await().atMost(java.time.Duration.ofSeconds(15)).untilAsserted(() -> {
            assertThat(notificationRepository.findById(payload.getNotificationId()))
                    .isPresent()
                    .get()
                    .extracting(e -> e.getStatus())
                    .isEqualTo(NotificationStatus.SENT);
            assertThat(queueDepth(RabbitNames.EMAIL_QUEUE)).isZero();
        });
    }

    @Test
    void slowRecipientEventuallyAcked() {
        NotificationMessage payload = message(NotificationChannel.EMAIL, "slow@example.com");

        rabbitTemplate.convertAndSend(RabbitNames.DIRECT_EXCHANGE, RabbitNames.ROUTING_EMAIL, payload);

        await().atMost(java.time.Duration.ofSeconds(15)).untilAsserted(() -> {
            var record = notificationRepository.findById(payload.getNotificationId());
            assertThat(record).isPresent();
            assertThat(record.get().getStatus()).isEqualTo(NotificationStatus.SENT);
            assertThat(queueDepth(RabbitNames.EMAIL_QUEUE)).isZero();
        });
    }

    @Test
    void smsSlowRecipientAcked() {
        NotificationMessage payload = message(NotificationChannel.SMS, "+15550100004");

        rabbitTemplate.convertAndSend(RabbitNames.DIRECT_EXCHANGE, RabbitNames.ROUTING_SMS, payload);

        await().atMost(java.time.Duration.ofSeconds(15)).untilAsserted(() -> {
            var record = notificationRepository.findById(payload.getNotificationId());
            assertThat(record).isPresent();
            assertThat(record.get().getStatus()).isEqualTo(NotificationStatus.SENT);
            assertThat(queueDepth(RabbitNames.SMS_QUEUE)).isZero();
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
                "manual-ack test",
                Instant.now(),
                "manual-ack-it",
                0
        );
    }
}
