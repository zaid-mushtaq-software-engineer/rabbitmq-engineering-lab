package com.example.notificationlab.consumer.config;

import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConsumerListenerConfig {

    @Bean
    SimpleRabbitListenerContainerFactory emailListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter messageConverter,
            @Value("${lab.consumer.email.concurrency:2}") int concurrency,
            @Value("${lab.consumer.email.max-concurrency:4}") int maxConcurrency,
            @Value("${lab.consumer.email.prefetch:10}") int prefetch
    ) {
        return buildFactory(connectionFactory, messageConverter, concurrency, maxConcurrency, prefetch);
    }

    @Bean
    SimpleRabbitListenerContainerFactory smsListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter messageConverter,
            @Value("${lab.consumer.sms.concurrency:2}") int concurrency,
            @Value("${lab.consumer.sms.max-concurrency:4}") int maxConcurrency,
            @Value("${lab.consumer.sms.prefetch:10}") int prefetch
    ) {
        return buildFactory(connectionFactory, messageConverter, concurrency, maxConcurrency, prefetch);
    }

    private static SimpleRabbitListenerContainerFactory buildFactory(
            ConnectionFactory connectionFactory,
            MessageConverter messageConverter,
            int concurrency,
            int maxConcurrency,
            int prefetch
    ) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setConcurrentConsumers(concurrency);
        factory.setMaxConcurrentConsumers(maxConcurrency);
        factory.setPrefetchCount(prefetch);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        return factory;
    }
}
