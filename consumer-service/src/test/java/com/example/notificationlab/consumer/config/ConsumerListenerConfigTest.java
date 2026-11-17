package com.example.notificationlab.consumer.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class ConsumerListenerConfigTest {

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
        registry.add("lab.consumer.email.concurrency", () -> "3");
        registry.add("lab.consumer.email.max-concurrency", () -> "3");
        registry.add("lab.consumer.email.prefetch", () -> "5");
        registry.add("lab.consumer.sms.concurrency", () -> "1");
        registry.add("lab.consumer.sms.max-concurrency", () -> "1");
        registry.add("lab.consumer.sms.prefetch", () -> "1");
        registry.add("lab.consumer.sms.listener-enabled", () -> "true");
    }

    @Autowired
    @Qualifier("emailListenerContainerFactory")
    private SimpleRabbitListenerContainerFactory emailListenerContainerFactory;

    @Autowired
    @Qualifier("smsListenerContainerFactory")
    private SimpleRabbitListenerContainerFactory smsListenerContainerFactory;

    @Test
    void factoriesHonorPerChannelConcurrencyAndPrefetch() {
        assertThat(ReflectionTestUtils.getField(emailListenerContainerFactory, "concurrentConsumers")).isEqualTo(3);
        assertThat(ReflectionTestUtils.getField(emailListenerContainerFactory, "maxConcurrentConsumers")).isEqualTo(3);
        assertThat(ReflectionTestUtils.getField(emailListenerContainerFactory, "prefetchCount")).isEqualTo(5);

        assertThat(ReflectionTestUtils.getField(smsListenerContainerFactory, "concurrentConsumers")).isEqualTo(1);
        assertThat(ReflectionTestUtils.getField(smsListenerContainerFactory, "maxConcurrentConsumers")).isEqualTo(1);
        assertThat(ReflectionTestUtils.getField(smsListenerContainerFactory, "prefetchCount")).isEqualTo(1);
    }
}