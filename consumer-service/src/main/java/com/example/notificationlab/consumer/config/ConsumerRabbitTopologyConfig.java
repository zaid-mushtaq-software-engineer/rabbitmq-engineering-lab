package com.example.notificationlab.consumer.config;

import com.example.notificationlab.contracts.RabbitNames;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class ConsumerRabbitTopologyConfig {

  @Bean
  DirectExchange notificationDirectExchange() {
    return new DirectExchange(RabbitNames.DIRECT_EXCHANGE, true, false);
  }

  @Bean
  DirectExchange notificationDlxExchange() {
    return new DirectExchange(RabbitNames.DLX_EXCHANGE, true, false);
  }

  @Bean
  DirectExchange notificationRetryExchange() {
    return new DirectExchange(RabbitNames.RETRY_EXCHANGE, true, false);
  }

  @Bean
  Queue deadLetterQueue() {
    return QueueBuilder.durable(RabbitNames.DEAD_LETTER_QUEUE).build();
  }

  @Bean
  Binding deadLetterBinding(Queue deadLetterQueue, DirectExchange notificationDlxExchange) {
    return BindingBuilder.bind(deadLetterQueue)
        .to(notificationDlxExchange)
        .with(RabbitNames.ROUTING_DEAD);
  }

  @Bean
  Queue emailQueue() {
    return QueueBuilder.durable(RabbitNames.EMAIL_QUEUE)
        .deadLetterExchange(RabbitNames.DLX_EXCHANGE)
        .deadLetterRoutingKey(RabbitNames.ROUTING_DEAD)
        .build();
  }

  @Bean
  Binding emailBinding(Queue emailQueue, DirectExchange notificationDirectExchange) {
    return BindingBuilder.bind(emailQueue)
        .to(notificationDirectExchange)
        .with(RabbitNames.ROUTING_EMAIL);
  }

  @Bean
  Queue smsQueue() {
    return QueueBuilder.durable(RabbitNames.SMS_QUEUE)
        .deadLetterExchange(RabbitNames.DLX_EXCHANGE)
        .deadLetterRoutingKey(RabbitNames.ROUTING_DEAD)
        .build();
  }

  @Bean
  Binding smsBinding(Queue smsQueue, DirectExchange notificationDirectExchange) {
    return BindingBuilder.bind(smsQueue)
        .to(notificationDirectExchange)
        .with(RabbitNames.ROUTING_SMS);
  }

  @Bean
  Queue emailRetryQueue(@Value("${lab.retry.delay:10s}") Duration retryDelay) {
    return QueueBuilder.durable(RabbitNames.EMAIL_RETRY_QUEUE)
        .ttl((int) retryDelay.toMillis())
        .deadLetterExchange(RabbitNames.DIRECT_EXCHANGE)
        .deadLetterRoutingKey(RabbitNames.ROUTING_EMAIL)
        .build();
  }

  @Bean
  Binding emailRetryBinding(Queue emailRetryQueue, DirectExchange notificationRetryExchange) {
    return BindingBuilder.bind(emailRetryQueue)
        .to(notificationRetryExchange)
        .with(RabbitNames.ROUTING_EMAIL_RETRY);
  }

  @Bean
  Queue smsRetryQueue(@Value("${lab.retry.delay:10s}") Duration retryDelay) {
    return QueueBuilder.durable(RabbitNames.SMS_RETRY_QUEUE)
        .ttl((int) retryDelay.toMillis())
        .deadLetterExchange(RabbitNames.DIRECT_EXCHANGE)
        .deadLetterRoutingKey(RabbitNames.ROUTING_SMS)
        .build();
  }

  @Bean
  Binding smsRetryBinding(Queue smsRetryQueue, DirectExchange notificationRetryExchange) {
    return BindingBuilder.bind(smsRetryQueue)
        .to(notificationRetryExchange)
        .with(RabbitNames.ROUTING_SMS_RETRY);
  }
}