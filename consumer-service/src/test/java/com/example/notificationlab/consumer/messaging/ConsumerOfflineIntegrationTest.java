package com.example.notificationlab.consumer.messaging;

import com.example.notificationlab.contracts.NotificationChannel;
import com.example.notificationlab.contracts.NotificationMessage;
import com.example.notificationlab.contracts.RabbitNames;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.MessageListenerContainer;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
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
class ConsumerOfflineIntegrationTest {

    private static final int MESSAGE_COUNT = 3;

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
    private RabbitListenerEndpointRegistry listenerRegistry;

    @BeforeEach
    void stopAllListeners() {
        listenerRegistry.getListenerContainerIds().forEach(id -> {
            MessageListenerContainer container = listenerRegistry.getListenerContainer(id);
            if (container.isRunning()) {
                container.stop();
            }
        });
        rabbitAdmin.purgeQueue(RabbitNames.EMAIL_QUEUE, false);
    }

    @Test
    void messagesWaitInQueueUntilListenersStart() {
        for (int i = 0; i < MESSAGE_COUNT; i++) {
            NotificationMessage payload = message();
            rabbitTemplate.convertAndSend(RabbitNames.DIRECT_EXCHANGE, RabbitNames.ROUTING_EMAIL, payload);
        }

        assertThat(queueDepth(RabbitNames.EMAIL_QUEUE)).isEqualTo(MESSAGE_COUNT);

        startListener("emailNotificationListener");
        startListener("smsNotificationListener");

        await().atMost(java.time.Duration.ofSeconds(30)).untilAsserted(() -> {
            assertThat(queueDepth(RabbitNames.EMAIL_QUEUE)).isZero();
        });
    }

    private void startListener(String id) {
        MessageListenerContainer container = listenerRegistry.getListenerContainer(id);
        if (!container.isRunning()) {
            container.start();
        }
    }

    private int queueDepth(String queue) {
        Properties props = rabbitAdmin.getQueueProperties(queue);
        return props == null ? 0 : Integer.parseInt(props.get("QUEUE_MESSAGE_COUNT").toString());
    }

    private static NotificationMessage message() {
        return new NotificationMessage(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                NotificationChannel.EMAIL,
                "offline-" + UUID.randomUUID() + "@example.com",
                "consumer offline test",
                Instant.now(),
                "offline-it",
                0
        );
    }
}
