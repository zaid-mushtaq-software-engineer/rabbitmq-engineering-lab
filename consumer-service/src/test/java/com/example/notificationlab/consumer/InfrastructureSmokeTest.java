package com.example.notificationlab.consumer;

import com.rabbitmq.client.ConnectionFactory;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class InfrastructureSmokeTest {

    @Container
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:4-management");

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("notification_lab")
            .withUsername("test")
            .withPassword("test");

    @Test
    void rabbitMqAcceptsAmqpConnection() throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(RABBIT.getHost());
        factory.setPort(RABBIT.getAmqpPort());
        factory.setUsername(RABBIT.getAdminUsername());
        factory.setPassword(RABBIT.getAdminPassword());
        try (var connection = factory.newConnection(); var channel = connection.createChannel()) {
            assertThat(channel.isOpen()).isTrue();
        }
    }

    @Test
    void mysqlAcceptsJdbcConnection() throws Exception {
        try (var connection = DriverManager.getConnection(
                MYSQL.getJdbcUrl(),
                MYSQL.getUsername(),
                MYSQL.getPassword())) {
            assertThat(connection.isValid(5)).isTrue();
        }
    }
}
