package com.example.notificationlab.producer.config;

import com.example.notificationlab.contracts.RabbitNames;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration
public class IntegrationRabbitTopologyConfig {

    @Bean
    Queue emailQueue() {
        return QueueBuilder.durable(RabbitNames.EMAIL_QUEUE).build();
    }

    @Bean
    Binding emailBinding(Queue emailQueue, org.springframework.amqp.core.DirectExchange notificationDirectExchange) {
        return BindingBuilder.bind(emailQueue).to(notificationDirectExchange).with(RabbitNames.ROUTING_EMAIL);
    }

    @Bean
    Queue smsQueue() {
        return QueueBuilder.durable(RabbitNames.SMS_QUEUE).build();
    }

    @Bean
    Binding smsBinding(Queue smsQueue, org.springframework.amqp.core.DirectExchange notificationDirectExchange) {
        return BindingBuilder.bind(smsQueue).to(notificationDirectExchange).with(RabbitNames.ROUTING_SMS);
    }
}
