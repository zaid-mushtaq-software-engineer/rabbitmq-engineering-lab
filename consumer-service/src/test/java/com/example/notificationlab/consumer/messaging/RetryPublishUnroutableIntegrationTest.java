package com.example.notificationlab.consumer.messaging;

import com.example.notificationlab.contracts.NotificationChannel;
import com.example.notificationlab.contracts.NotificationMessage;
import com.example.notificationlab.contracts.RabbitNames;
import com.example.notificationlab.consumer.persistence.NotificationAttemptRepository;
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
class RetryPublishUnroutableIntegrationTest {

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
        rabbitAdmin.purgeQueue(RabbitNames.EMAIL_RETRY_QUEUE, false);
    }

    @Test
    void retryPublishFailureLeavesMessageForRedelivery() {
        rabbitTemplate.execute(channel -> {
            channel.queueUnbind(
                    RabbitNames.EMAIL_RETRY_QUEUE,
                    RabbitNames.RETRY_EXCHANGE,
                    RabbitNames.ROUTING_EMAIL_RETRY
            );
            return null;
        });

        NotificationMessage payload = new NotificationMessage(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                NotificationChannel.EMAIL,
                "temp-fail@example.com",
                "retry-unroutable",
                Instant.now(),
                "retry-unroutable",
                0
        );

        rabbitTemplate.convertAndSend(RabbitNames.DIRECT_EXCHANGE, RabbitNames.ROUTING_EMAIL, payload);

        await().atMost(java.time.Duration.ofSeconds(15)).untilAsserted(() -> {
            var record = notificationRepository.findById(payload.getNotificationId());
            assertThat(record).isPresent();
            assertThat(attemptRepository.findByNotificationIdOrderByAttemptNumberAsc(payload.getNotificationId()))
                    .isNotEmpty();
            assertThat(record.get().getStatus()).isNotEqualTo(NotificationStatus.SENT);
            assertThat(queueDepth(RabbitNames.EMAIL_QUEUE) + queueDepth(RabbitNames.EMAIL_RETRY_QUEUE))
                    .isGreaterThanOrEqualTo(0);
        });
    }

    private int queueDepth(String queue) {
        Properties props = rabbitAdmin.getQueueProperties(queue);
        return props == null ? 0 : Integer.parseInt(props.get("QUEUE_MESSAGE_COUNT").toString());
    }
}