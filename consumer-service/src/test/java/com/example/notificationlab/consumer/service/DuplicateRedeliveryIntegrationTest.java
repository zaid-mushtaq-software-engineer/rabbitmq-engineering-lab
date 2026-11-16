package com.example.notificationlab.consumer.service;

import com.example.notificationlab.contracts.NotificationChannel;
import com.example.notificationlab.contracts.NotificationMessage;
import com.example.notificationlab.contracts.RabbitNames;
import com.example.notificationlab.consumer.messaging.MessageDeliveryHandler;
import com.example.notificationlab.consumer.persistence.NotificationAttemptRepository;
import com.example.notificationlab.consumer.persistence.NotificationRepository;
import com.example.notificationlab.consumer.persistence.NotificationStatus;
import com.example.notificationlab.consumer.persistence.ProcessedMessageRepository;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * T-7.4 — outcome is durable before ack; a second delivery of the same {@code messageId} does not add outcomes.
 */
@SpringBootTest
@Testcontainers
class DuplicateRedeliveryIntegrationTest {

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
        registry.add("lab.consumer.ack.defer-for-tests", () -> "true");
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

    @Autowired
    private MessageDeliveryHandler deliveryHandler;

    @BeforeEach
    void reset() {
        rabbitAdmin.purgeQueue(RabbitNames.EMAIL_QUEUE, false);
        deliveryHandler.clearDeferAck();
    }

    @Test
    void commitBeforeAckSecondDeliveryIsHarmlessDuplicate() {
        NotificationMessage payload = message(NotificationChannel.EMAIL, "alice@example.com");
        deliveryHandler.setDeferAckMessageId(payload.getMessageId());

        rabbitTemplate.convertAndSend(RabbitNames.DIRECT_EXCHANGE, RabbitNames.ROUTING_EMAIL, payload);

        await().atMost(java.time.Duration.ofSeconds(15)).untilAsserted(() -> {
            assertThat(notificationRepository.findById(payload.getNotificationId()))
                    .isPresent()
                    .get()
                    .extracting(e -> e.getStatus())
                    .isEqualTo(NotificationStatus.SENT);
            assertThat(processedMessageRepository.findById(payload.getMessageId())).isPresent();
        });

        rabbitTemplate.convertAndSend(RabbitNames.DIRECT_EXCHANGE, RabbitNames.ROUTING_EMAIL, payload);

        await().atMost(java.time.Duration.ofSeconds(15)).untilAsserted(() -> {
            assertThat(attemptRepository.findByNotificationIdOrderByAttemptNumberAsc(payload.getNotificationId()))
                    .hasSize(1);
            assertThat(processedMessageRepository.findAll()).hasSize(1);
        });

        deliveryHandler.clearDeferAck();
    }

    private static NotificationMessage message(NotificationChannel channel, String recipient) {
        return new NotificationMessage(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                channel,
                recipient,
                "phase-7-redelivery",
                Instant.now(),
                "phase-7",
                0
        );
    }
}