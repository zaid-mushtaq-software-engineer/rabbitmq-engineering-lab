package com.example.notificationlab.consumer.service;

import com.example.notificationlab.contracts.NotificationChannel;
import com.example.notificationlab.contracts.NotificationMessage;
import com.example.notificationlab.contracts.RabbitNames;
import com.example.notificationlab.consumer.persistence.NotificationRepository;
import com.example.notificationlab.consumer.persistence.NotificationStatus;
import org.junit.jupiter.api.Test;
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
    }

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private NotificationRepository notificationRepository;

    @Test
    void publishedMessageEndsAsSent() {
        String notificationId = UUID.randomUUID().toString();
        String messageId = UUID.randomUUID().toString();
        NotificationMessage payload = new NotificationMessage(
                messageId,
                notificationId,
                NotificationChannel.EMAIL,
                "alice@example.com",
                "integration test",
                Instant.now(),
                "it-1",
                0
        );

        rabbitTemplate.convertAndSend(RabbitNames.DIRECT_EXCHANGE, RabbitNames.ROUTING_EMAIL, payload);

        await().atMost(java.time.Duration.ofSeconds(30)).untilAsserted(() -> {
            var notification = notificationRepository.findById(notificationId);
            assertThat(notification).isPresent();
            assertThat(notification.get().getStatus()).isEqualTo(NotificationStatus.SENT);
            assertThat(notification.get().getAttemptCount()).isEqualTo(1);
        });
    }
}
