package com.example.notificationlab.producer.messaging;

import com.example.notificationlab.contracts.NotificationChannel;
import com.example.notificationlab.contracts.RabbitNames;
import com.example.notificationlab.producer.config.IntegrationRabbitTopologyConfig;
import com.example.notificationlab.producer.config.ProducerRabbitTopologyConfig;
import com.example.notificationlab.producer.config.RabbitMessagingConfig;
import com.example.notificationlab.producer.service.MessageFactory;
import com.example.notificationlab.producer.service.RoutingKeySelector;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
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

import java.time.Duration;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(classes = PersistentPublishIntegrationTest.TestContext.class)
@Testcontainers
class PersistentPublishIntegrationTest {

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
        NotificationMessagePublisher notificationMessagePublisher(
                RabbitTemplate rabbitTemplate,
                RoutingKeySelector routingKeySelector
        ) {
            return new NotificationMessagePublisher(rabbitTemplate, routingKeySelector);
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
    }

    @Autowired
    private NotificationMessagePublisher publisher;

    @Autowired
    private MessageFactory messageFactory;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RabbitAdmin rabbitAdmin;

    @Test
    void publishedMessagesArePersistent() {
        var built = messageFactory.build(
                NotificationChannel.EMAIL,
                "alice@example.com",
                "persistence test",
                "persist-it"
        );
        publisher.publish(built.message());

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(queueDepth(RabbitNames.EMAIL_QUEUE)).isEqualTo(1));

        rabbitTemplate.execute(channel -> {
            var response = channel.basicGet(RabbitNames.EMAIL_QUEUE, false);
            assertThat(response).isNotNull();
            var properties = response.getProps();
            assertThat(properties.getDeliveryMode()).isEqualTo(2);
            assertThat(properties.getContentType()).contains("json");
            assertThat(properties.getMessageId()).isEqualTo(built.messageId());
            channel.basicAck(response.getEnvelope().getDeliveryTag(), false);
            return null;
        });

        rabbitAdmin.purgeQueue(RabbitNames.EMAIL_QUEUE, false);
    }

    private int queueDepth(String queue) {
        Properties props = rabbitAdmin.getQueueProperties(queue);
        return props == null ? 0 : Integer.parseInt(props.get("QUEUE_MESSAGE_COUNT").toString());
    }
}
