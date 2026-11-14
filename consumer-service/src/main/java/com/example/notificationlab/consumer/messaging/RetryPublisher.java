package com.example.notificationlab.consumer.messaging;

import com.example.notificationlab.contracts.NotificationChannel;
import com.example.notificationlab.contracts.NotificationMessage;
import com.example.notificationlab.contracts.RabbitNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class RetryPublisher {

    private static final Logger log = LoggerFactory.getLogger(RetryPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final Duration confirmTimeout;

    public RetryPublisher(
            RabbitTemplate rabbitTemplate,
            @Value("${lab.publisher.confirm-timeout:5s}") Duration confirmTimeout
    ) {
        this.rabbitTemplate = rabbitTemplate;
        this.confirmTimeout = confirmTimeout;
    }

    public boolean publishWithConfirm(NotificationMessage message) {
        String routingKey = message.getChannel() == NotificationChannel.EMAIL
                ? RabbitNames.ROUTING_EMAIL_RETRY
                : RabbitNames.ROUTING_SMS_RETRY;
        MessagePostProcessor persistent = msg -> {
            msg.getMessageProperties().setDeliveryMode(org.springframework.amqp.core.MessageDeliveryMode.PERSISTENT);
            msg.getMessageProperties().setMessageId(message.getMessageId());
            msg.getMessageProperties().setCorrelationId(message.getCorrelationId());
            return msg;
        };
        CorrelationData correlationData = new CorrelationData(
                message.getMessageId() + "-retry-" + message.getRetryCount()
        );
        try {
            Boolean confirmed = rabbitTemplate.invoke(operations -> {
                operations.convertAndSend(
                        RabbitNames.RETRY_EXCHANGE,
                        routingKey,
                        message,
                        persistent,
                        correlationData
                );
                return operations.waitForConfirms(confirmTimeout.toMillis());
            });
            boolean acked = Boolean.TRUE.equals(confirmed);
            if (acked) {
                log.info("retry.scheduled messageId={} retryCount={}", message.getMessageId(), message.getRetryCount());
            } else {
                log.warn("retry.publish.not_confirmed messageId={} retryCount={}", message.getMessageId(), message.getRetryCount());
            }
            return acked;
        } catch (Exception ex) {
            log.warn("retry.publish.failed messageId={}: {}", message.getMessageId(), ex.getMessage());
            return false;
        }
    }
}