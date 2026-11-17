package com.example.notificationlab.producer.messaging;

import com.example.notificationlab.contracts.NotificationChannel;
import com.example.notificationlab.producer.config.IntegrationRabbitTopologyConfig;
import com.example.notificationlab.producer.config.ProducerRabbitTopologyConfig;
import com.example.notificationlab.producer.config.RabbitMessagingConfig;
import com.example.notificationlab.producer.service.MessageFactory;
import com.example.notificationlab.producer.service.RoutingKeySelector;
import com.example.notificationlab.producer.observability.ProducerNotificationMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = PublisherConfirmIntegrationTest.TestContext.class)
@Testcontainers
class PublisherConfirmIntegrationTest {

    @Configuration
    @Import({
            ProducerRabbitTopologyConfig.class,
            RabbitMessagingConfig.class,
            IntegrationRabbitTopologyConfig.class
    })
    @ImportAutoConfiguration({RabbitAutoConfiguration.class, JacksonAutoConfiguration.class})
    static class TestContext {

        @Bean
        MessageFactory messageFactory() {
            return new MessageFactory();
        }

        @Bean
        RoutingKeySelector routingKeySelector() {
            return new RoutingKeySelector();
        }

        @Bean
        SimpleMeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }

        @Bean
        ProducerNotificationMetrics producerNotificationMetrics(SimpleMeterRegistry meterRegistry) {
            return new ProducerNotificationMetrics(meterRegistry);
        }

        @Bean
        PublisherConfirmCoordinator publisherConfirmCoordinator(
                org.springframework.amqp.rabbit.core.RabbitTemplate rabbitTemplate
        ) {
            return new PublisherConfirmCoordinator(rabbitTemplate);
        }

        @Bean
        NotificationMessagePublisher notificationMessagePublisher(
                org.springframework.amqp.rabbit.core.RabbitTemplate rabbitTemplate,
                RoutingKeySelector routingKeySelector,
                PublisherConfirmCoordinator publisherConfirmCoordinator,
                ProducerNotificationMetrics producerNotificationMetrics
        ) {
            return new NotificationMessagePublisher(
                    rabbitTemplate,
                    routingKeySelector,
                    publisherConfirmCoordinator,
                    producerNotificationMetrics,
                    java.time.Duration.ofSeconds(5)
            );
        }
    }

    @Container
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:4-management");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", RABBIT::getHost);
        registry.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBIT::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBIT::getAdminPassword);
        registry.add("spring.rabbitmq.publisher-confirm-type", () -> "correlated");
        registry.add("spring.rabbitmq.publisher-returns", () -> "true");
    }

    @Autowired
    private NotificationMessagePublisher publisher;

    @Autowired
    private MessageFactory messageFactory;

    @Test
    void publishWithConfirmAcknowledgesFromBroker() {
        var built = messageFactory.build(
                NotificationChannel.EMAIL,
                "alice@example.com",
                "confirm test",
                "confirm-it"
        );

        PublishResult result = publisher.publishWithConfirm(built.message());

        assertThat(result.outcome()).isEqualTo(PublishOutcome.CONFIRMED);
    }
}