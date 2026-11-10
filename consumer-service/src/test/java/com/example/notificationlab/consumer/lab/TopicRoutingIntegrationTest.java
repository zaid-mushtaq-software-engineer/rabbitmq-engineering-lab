package com.example.notificationlab.consumer.lab;

import com.example.notificationlab.contracts.RabbitNames;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@Testcontainers
@ActiveProfiles("lab-exchanges")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TopicRoutingIntegrationTest {

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
        registry.add("lab.consumer.lab-exchange.listener-enabled", () -> "false");
    }

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RabbitAdmin rabbitAdmin;

    @BeforeEach
    void purgeLabQueues() {
        rabbitAdmin.purgeQueue(RabbitNames.LAB_TOPIC_ALL_CHANNELS_QUEUE, false);
        rabbitAdmin.purgeQueue(RabbitNames.LAB_TOPIC_EMAIL_ANY_QUEUE, false);
        rabbitAdmin.purgeQueue(RabbitNames.LAB_TOPIC_HIGH_PRIORITY_QUEUE, false);
    }

    @Test
    void routingMatchesFlowMatrix() {
        Map<String, int[]> expected = Map.of(
                "notification.email", new int[]{1, 1, 0},
                "notification.sms", new int[]{1, 0, 0},
                "notification.push", new int[]{1, 0, 0},
                "notification.email.high", new int[]{0, 1, 1},
                "notification.sms.high", new int[]{0, 0, 1},
                "order.created", new int[]{0, 0, 0}
        );

        expected.forEach((routingKey, counts) -> {
            purgeLabQueues();
            rabbitTemplate.convertAndSend(RabbitNames.TOPIC_EXCHANGE, routingKey, "lab-" + routingKey);
            await().atMost(java.time.Duration.ofSeconds(5)).untilAsserted(() -> {
                assertThat(depth(RabbitNames.LAB_TOPIC_ALL_CHANNELS_QUEUE)).isEqualTo(counts[0]);
                assertThat(depth(RabbitNames.LAB_TOPIC_EMAIL_ANY_QUEUE)).isEqualTo(counts[1]);
                assertThat(depth(RabbitNames.LAB_TOPIC_HIGH_PRIORITY_QUEUE)).isEqualTo(counts[2]);
            });
        });
    }

    private int depth(String queue) {
        Properties props = rabbitAdmin.getQueueProperties(queue);
        return props == null ? 0 : Integer.parseInt(props.get("QUEUE_MESSAGE_COUNT").toString());
    }
}
