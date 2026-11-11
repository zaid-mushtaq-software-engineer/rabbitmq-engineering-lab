package com.example.notificationlab.consumer.listener;

import com.example.notificationlab.contracts.NotificationChannel;
import com.example.notificationlab.contracts.NotificationMessage;
import com.example.notificationlab.consumer.messaging.DeliveryOutcome;
import com.example.notificationlab.consumer.messaging.MessageDeliveryHandler;
import com.example.notificationlab.consumer.service.NotificationProcessingService;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class ChannelNotificationListenerSupport {

    private static final Logger log = LoggerFactory.getLogger(ChannelNotificationListenerSupport.class);

    private final NotificationProcessingService processingService;
    private final MessageDeliveryHandler deliveryHandler;

    public ChannelNotificationListenerSupport(
            NotificationProcessingService processingService,
            MessageDeliveryHandler deliveryHandler
    ) {
        this.processingService = processingService;
        this.deliveryHandler = deliveryHandler;
    }

    public void handle(
            NotificationMessage message,
            boolean redelivered,
            NotificationChannel expectedChannel,
            String consumerName,
            String routingKey,
            Channel channel,
            long deliveryTag
    ) {
        log.info("message.received messageId={} routingKey={} redelivered={}",
                message.getMessageId(), routingKey, redelivered);
        DeliveryOutcome outcome;
        try {
            outcome = processingService.process(message, redelivered, expectedChannel, consumerName);
        } catch (RuntimeException ex) {
            log.error("Unexpected error processing messageId={}", message.getMessageId(), ex);
            outcome = DeliveryOutcome.NACK_NO_REQUEUE;
        }
        try {
            deliveryHandler.apply(channel, deliveryTag, outcome, message.getMessageId());
        } catch (IOException | IllegalStateException ex) {
            log.warn("Broker ack/nack failed after processing messageId={}: {}",
                    message.getMessageId(), ex.getMessage());
        }
    }
}
