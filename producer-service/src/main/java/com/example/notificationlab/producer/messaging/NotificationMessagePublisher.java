package com.example.notificationlab.producer.messaging;

import com.example.notificationlab.contracts.NotificationMessage;
import com.example.notificationlab.contracts.RabbitNames;
import com.example.notificationlab.producer.observability.ProducerNotificationMetrics;
import com.example.notificationlab.producer.service.RoutingKeySelector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class NotificationMessagePublisher {

    private static final Logger log = LoggerFactory.getLogger(NotificationMessagePublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final RoutingKeySelector routingKeySelector;
    private final PublisherConfirmCoordinator confirmCoordinator;
    private final ProducerNotificationMetrics metrics;
    private final Duration confirmTimeout;

    public NotificationMessagePublisher(
            RabbitTemplate rabbitTemplate,
            RoutingKeySelector routingKeySelector,
            PublisherConfirmCoordinator confirmCoordinator,
            ProducerNotificationMetrics metrics,
            @Value("${lab.publisher.confirm-timeout:5s}") Duration confirmTimeout
    ) {
        this.rabbitTemplate = rabbitTemplate;
        this.routingKeySelector = routingKeySelector;
        this.confirmCoordinator = confirmCoordinator;
        this.metrics = metrics;
        this.confirmTimeout = confirmTimeout;
    }

    public PublishResult publishWithConfirm(NotificationMessage notificationMessage) {
        String routingKey = routingKeySelector.select(notificationMessage.getChannel());
        String trackingId = confirmCoordinator.beginPublish();
        CorrelationData correlationData = new CorrelationData(trackingId);
        try {
            rabbitTemplate.convertAndSend(
                    RabbitNames.DIRECT_EXCHANGE,
                    routingKey,
                    notificationMessage,
                    message -> {
                        MessageProperties properties = message.getMessageProperties();
                        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
                        properties.setMessageId(notificationMessage.getMessageId());
                        properties.setCorrelationId(trackingId);
                        if (notificationMessage.getCreatedAt() != null) {
                            properties.setTimestamp(java.util.Date.from(notificationMessage.getCreatedAt()));
                        }
                        return message;
                    },
                    correlationData
            );
            PublishResult result = confirmCoordinator.awaitConfirm(trackingId, confirmTimeout.toMillis());
            metrics.recordPublishResult(result.outcome());
            if (result.isConfirmed()) {
                log.info("publish.confirmed messageId={}", notificationMessage.getMessageId());
            } else {
                log.warn("publish.failed messageId={} outcome={}", notificationMessage.getMessageId(), result.outcome());
            }
            return result;
        } catch (AmqpException ex) {
            log.warn("publish.broker_unavailable messageId={}: {}", notificationMessage.getMessageId(), ex.getMessage());
            confirmCoordinator.publishFailedBeforeSend(trackingId);
            metrics.recordPublishResult(PublishOutcome.BROKER_UNAVAILABLE);
            return PublishResult.of(PublishOutcome.BROKER_UNAVAILABLE, ex.getMessage());
        }
    }
}