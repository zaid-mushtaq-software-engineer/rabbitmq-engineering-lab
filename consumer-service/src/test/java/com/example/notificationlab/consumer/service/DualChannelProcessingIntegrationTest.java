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
class DualChannelProcessingIntegrationTest {

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
        registry.add("lab.consumer.sms.listener-enabled", () -> "true");
    }

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private NotificationRepository notificationRepository;

    @Test
    void emailAndSmsBothReachSent() {
        NotificationMessage email = message(NotificationChannel.EMAIL, "alice@example.com");
        NotificationMessage sms = message(NotificationChannel.SMS, "+15550100099");

        rabbitTemplate.convertAndSend(RabbitNames.DIRECT_EXCHANGE, RabbitNames.ROUTING_EMAIL, email);
        rabbitTemplate.convertAndSend(RabbitNames.DIRECT_EXCHANGE, RabbitNames.ROUTING_SMS, sms);

        await().atMost(java.time.Duration.ofSeconds(30)).untilAsserted(() -> {
            var emailRecord = notificationRepository.findById(email.getNotificationId());
            assertThat(emailRecord).isPresent();
            assertThat(emailRecord.get().getStatus()).isEqualTo(NotificationStatus.SENT);
            var smsRecord = notificationRepository.findById(sms.getNotificationId());
            assertThat(smsRecord).isPresent();
            assertThat(smsRecord.get().getStatus()).isEqualTo(NotificationStatus.SENT);
        });
    }

    private static NotificationMessage message(NotificationChannel channel, String recipient) {
        return new NotificationMessage(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                channel,
                recipient,
                "dual-channel test",
                Instant.now(),
                "dual-it",
                0
        );
    }
}
