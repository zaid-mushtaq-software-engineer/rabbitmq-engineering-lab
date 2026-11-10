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

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@Testcontainers
@ActiveProfiles("lab-exchanges")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class FanoutRoutingIntegrationTest {

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
    void purge() {
        rabbitAdmin.purgeQueue(RabbitNames.LAB_FANOUT_AUDIT_QUEUE, false);
        rabbitAdmin.purgeQueue(RabbitNames.LAB_FANOUT_ANALYTICS_QUEUE, false);
    }

    @Test
    void fanoutDeliversToEveryBoundQueue() {
        rabbitTemplate.convertAndSend(RabbitNames.FANOUT_EXCHANGE, "ignored-key", "fanout-1");
        rabbitTemplate.convertAndSend(RabbitNames.FANOUT_EXCHANGE, "another", "fanout-2");

        await().atMost(java.time.Duration.ofSeconds(5)).untilAsserted(() -> {
            assertThat(depth(RabbitNames.LAB_FANOUT_AUDIT_QUEUE)).isEqualTo(2);
            assertThat(depth(RabbitNames.LAB_FANOUT_ANALYTICS_QUEUE)).isEqualTo(2);
        });
    }

    private int depth(String queue) {
        Properties props = rabbitAdmin.getQueueProperties(queue);
        return props == null ? 0 : Integer.parseInt(props.get("QUEUE_MESSAGE_COUNT").toString());
    }
}
