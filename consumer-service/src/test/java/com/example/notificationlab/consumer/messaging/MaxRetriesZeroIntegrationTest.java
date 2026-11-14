package com.example.notificationlab.consumer.messaging;

import com.example.notificationlab.contracts.NotificationChannel;
import com.example.notificationlab.contracts.NotificationMessage;
import com.example.notificationlab.contracts.RabbitNames;
import com.example.notificationlab.consumer.persistence.NotificationRepository;
import com.example.notificationlab.consumer.persistence.NotificationStatus;
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
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@Testcontainers
class MaxRetriesZeroIntegrationTest {

    @Container
    static final RabbitMQContainer MAX_RETRIES_RABBIT = new RabbitMQContainer("rabbitmq:4-management");

    @Container
    static final MySQLContainer<?> MAX_RETRIES_MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("notification_lab")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", () -> MAX_RETRIES_RABBIT.getHost());
        registry.add("spring.rabbitmq.port", () -> MAX_RETRIES_RABBIT.getAmqpPort());
        registry.add("spring.rabbitmq.username", () -> MAX_RETRIES_RABBIT.getAdminUsername());
        registry.add("spring.rabbitmq.password", () -> MAX_RETRIES_RABBIT.getAdminPassword());
        registry.add("spring.datasource.url", () -> MAX_RETRIES_MYSQL.getJdbcUrl());
        registry.add("spring.datasource.username", () -> MAX_RETRIES_MYSQL.getUsername());
        registry.add("spring.datasource.password", () -> MAX_RETRIES_MYSQL.getPassword());
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
        registry.add("lab.retry.max-retries", () -> "0");
        registry.add("lab.simulation.latency-min", () -> "0ms");
        registry.add("lab.simulation.latency-max", () -> "0ms");
    }

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RabbitAdmin rabbitAdmin;

    @Autowired
    private NotificationRepository notificationRepository;

    @BeforeEach
    void purgeQueues() {
        rabbitAdmin.purgeQueue(RabbitNames.EMAIL_QUEUE, false);
        rabbitAdmin.purgeQueue(RabbitNames.DEAD_LETTER_QUEUE, false);
    }

    @Test
    void temporaryFailureWithMaxRetriesZeroDeadLettersImmediately() {
        NotificationMessage payload = new NotificationMessage(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                NotificationChannel.EMAIL,
                "temp-fail@example.com",
                "max-retries-zero",
                Instant.now(),
                "max-retries-zero",
                0
        );

        rabbitTemplate.convertAndSend(RabbitNames.DIRECT_EXCHANGE, RabbitNames.ROUTING_EMAIL, payload);

        await().atMost(java.time.Duration.ofSeconds(10)).untilAsserted(() -> {
            var record = notificationRepository.findById(payload.getNotificationId()).orElseThrow();
            assertThat(record.getStatus()).isEqualTo(NotificationStatus.DEAD_LETTERED);
            assertThat(record.getAttemptCount()).isEqualTo(1);
            assertThat(queueDepth(RabbitNames.DEAD_LETTER_QUEUE)).isEqualTo(1);
        });
    }

    private int queueDepth(String queue) {
        Properties props = rabbitAdmin.getQueueProperties(queue);
        return props == null ? 0 : Integer.parseInt(props.get("QUEUE_MESSAGE_COUNT").toString());
    }
}