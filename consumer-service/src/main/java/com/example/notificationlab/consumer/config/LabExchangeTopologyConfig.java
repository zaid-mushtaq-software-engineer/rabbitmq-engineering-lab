package com.example.notificationlab.consumer.config;

import com.example.notificationlab.contracts.RabbitNames;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("lab-exchanges")
public class LabExchangeTopologyConfig {

    @Bean
    TopicExchange notificationTopicExchange() {
        return new TopicExchange(RabbitNames.TOPIC_EXCHANGE, true, false);
    }

    @Bean
    FanoutExchange notificationFanoutExchange() {
        return new FanoutExchange(RabbitNames.FANOUT_EXCHANGE, true, false);
    }

    @Bean
    Queue labTopicAllChannelsQueue() {
        return QueueBuilder.durable(RabbitNames.LAB_TOPIC_ALL_CHANNELS_QUEUE).build();
    }

    @Bean
    Queue labTopicEmailAnyQueue() {
        return QueueBuilder.durable(RabbitNames.LAB_TOPIC_EMAIL_ANY_QUEUE).build();
    }

    @Bean
    Queue labTopicHighPriorityQueue() {
        return QueueBuilder.durable(RabbitNames.LAB_TOPIC_HIGH_PRIORITY_QUEUE).build();
    }

    @Bean
    Binding labTopicAllChannelsBinding(Queue labTopicAllChannelsQueue, TopicExchange notificationTopicExchange) {
        return BindingBuilder.bind(labTopicAllChannelsQueue).to(notificationTopicExchange).with("notification.*");
    }

    @Bean
    Binding labTopicEmailAnyBinding(Queue labTopicEmailAnyQueue, TopicExchange notificationTopicExchange) {
        return BindingBuilder.bind(labTopicEmailAnyQueue).to(notificationTopicExchange).with("notification.email.#");
    }

    @Bean
    Binding labTopicHighPriorityBinding(Queue labTopicHighPriorityQueue, TopicExchange notificationTopicExchange) {
        return BindingBuilder.bind(labTopicHighPriorityQueue).to(notificationTopicExchange).with("notification.*.high");
    }

    @Bean
    Queue labFanoutAuditQueue() {
        return QueueBuilder.durable(RabbitNames.LAB_FANOUT_AUDIT_QUEUE).build();
    }

    @Bean
    Queue labFanoutAnalyticsQueue() {
        return QueueBuilder.durable(RabbitNames.LAB_FANOUT_ANALYTICS_QUEUE).build();
    }

    @Bean
    Binding labFanoutAuditBinding(Queue labFanoutAuditQueue, FanoutExchange notificationFanoutExchange) {
        return BindingBuilder.bind(labFanoutAuditQueue).to(notificationFanoutExchange);
    }

    @Bean
    Binding labFanoutAnalyticsBinding(Queue labFanoutAnalyticsQueue, FanoutExchange notificationFanoutExchange) {
        return BindingBuilder.bind(labFanoutAnalyticsQueue).to(notificationFanoutExchange);
    }
}
