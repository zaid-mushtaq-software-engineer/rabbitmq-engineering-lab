package com.example.notificationlab.consumer.messaging;

import com.example.notificationlab.contracts.RabbitNames;
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

@SpringBootTest
@Testcontainers
class RabbitTopologyIntegrationTest {

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
        registry.add("lab.retry.delay", () -> "1s");
    }

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Test
    void phase5TopologyQueuesAndExchangesExist() {
        rabbitTemplate.execute(channel -> {
            channel.queueDeclarePassive(RabbitNames.EMAIL_QUEUE);
            channel.queueDeclarePassive(RabbitNames.SMS_QUEUE);
            channel.queueDeclarePassive(RabbitNames.DEAD_LETTER_QUEUE);
            channel.queueDeclarePassive(RabbitNames.EMAIL_RETRY_QUEUE);
            channel.queueDeclarePassive(RabbitNames.SMS_RETRY_QUEUE);
            channel.exchangeDeclarePassive(RabbitNames.DLX_EXCHANGE);
            channel.exchangeDeclarePassive(RabbitNames.RETRY_EXCHANGE);
            return null;
        });
    }
}