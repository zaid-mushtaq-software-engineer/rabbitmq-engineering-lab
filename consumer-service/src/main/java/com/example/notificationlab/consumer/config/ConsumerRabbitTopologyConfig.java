package com.example.notificationlab.consumer.config;

import com.example.notificationlab.contracts.RabbitNames;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConsumerRabbitTopologyConfig {

    @Bean
    DirectExchange notificationDirectExchange() {
        return new DirectExchange(RabbitNames.DIRECT_EXCHANGE, true, false);
    }

    @Bean
    Queue emailQueue() {
        return QueueBuilder.durable(RabbitNames.EMAIL_QUEUE).build();
    }

    @Bean
    Binding emailBinding(Queue emailQueue, DirectExchange notificationDirectExchange) {
        return BindingBuilder.bind(emailQueue)
                .to(notificationDirectExchange)
                .with(RabbitNames.ROUTING_EMAIL);
    }
}
