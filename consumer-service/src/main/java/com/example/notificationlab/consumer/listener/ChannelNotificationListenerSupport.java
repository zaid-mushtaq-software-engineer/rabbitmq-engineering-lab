package com.example.notificationlab.consumer.listener;

import com.example.notificationlab.contracts.NotificationChannel;
import com.example.notificationlab.contracts.NotificationMessage;
import com.example.notificationlab.consumer.messaging.DeliveryOutcome;
import com.example.notificationlab.consumer.messaging.MalformedMessageException;
import com.example.notificationlab.consumer.messaging.MessageDeliveryHandler;
import com.example.notificationlab.consumer.messaging.ProcessingResult;
import com.example.notificationlab.consumer.messaging.RetryPublisher;
import com.example.notificationlab.consumer.service.NotificationProcessingService;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class ChannelNotificationListenerSupport {

    private static final Logger log = LoggerFactory.getLogger(ChannelNotificationListenerSupport.class);

    private final NotificationProcessingService processingService;
    private final MessageDeliveryHandler deliveryHandler;
    private final RetryPublisher retryPublisher;
    private final MessageConverter messageConverter;

    public ChannelNotificationListenerSupport(
            NotificationProcessingService processingService,
            MessageDeliveryHandler deliveryHandler,
            RetryPublisher retryPublisher,
            MessageConverter messageConverter
    ) {
        this.processingService = processingService;
        this.deliveryHandler = deliveryHandler;
        this.retryPublisher = retryPublisher;
        this.messageConverter = messageConverter;
    }

    public void handle(
            Message amqpMessage,
            boolean redelivered,
            NotificationChannel expectedChannel,
            String consumerName,
            String routingKey,
            Channel channel,
            long deliveryTag
    ) {
        NotificationMessage message;
        try {
            Object payload = messageConverter.fromMessage(amqpMessage);
            if (!(payload instanceof NotificationMessage notificationMessage)) {
                throw new MalformedMessageException("Expected NotificationMessage payload");
            }
            message = notificationMessage;
        } catch (MessageConversionException | MalformedMessageException ex) {
            log.warn("message.malformed routingKey={} redelivered={}: {}", routingKey, redelivered, ex.getMessage());
            finishMalformed(channel, deliveryTag, expectedChannel, consumerName, null, ex.getMessage());
            return;
        }

        try {
            validateMessage(message, expectedChannel);
        } catch (MalformedMessageException ex) {
            log.warn("message.malformed messageId={}: {}", message.getMessageId(), ex.getMessage());
            applyProcessingResult(
                    channel,
                    deliveryTag,
                    processingService.recordMalformedDeadLetter(message, expectedChannel, consumerName, ex.getMessage())
            );
            return;
        }

        log.info("message.received messageId={} routingKey={} redelivered={}",
                message.getMessageId(), routingKey, redelivered);
        ProcessingResult result;
        try {
            result = processingService.process(message, redelivered, expectedChannel, consumerName);
        } catch (RuntimeException ex) {
            log.error("Unexpected error processing messageId={}", message.getMessageId(), ex);
            applyOutcome(channel, deliveryTag, DeliveryOutcome.NACK_NO_REQUEUE, message.getMessageId());
            return;
        }
        applyProcessingResult(channel, deliveryTag, result);
    }

    private void finishMalformed(
            Channel channel,
            long deliveryTag,
            NotificationChannel expectedChannel,
            String consumerName,
            NotificationMessage partial,
            String reason
    ) {
        try {
            ProcessingResult result = processingService.recordMalformedDeadLetter(
                    partial != null ? partial : new NotificationMessage(),
                    expectedChannel,
                    consumerName,
                    reason
            );
            applyProcessingResult(channel, deliveryTag, result);
        } catch (RuntimeException ex) {
            log.error("Failed to record malformed message: {}", ex.getMessage());
            applyOutcome(channel, deliveryTag, DeliveryOutcome.NACK_NO_REQUEUE, "unknown");
        }
    }

    private void applyProcessingResult(Channel channel, long deliveryTag, ProcessingResult result) {
        switch (result.step()) {
            case ACK -> applyOutcome(channel, deliveryTag, DeliveryOutcome.ACK, result.messageId());
            case NACK_NO_REQUEUE -> applyOutcome(channel, deliveryTag, DeliveryOutcome.NACK_NO_REQUEUE, result.messageId());
            case PUBLISH_RETRY_THEN_ACK -> {
                boolean confirmed = retryPublisher.publishWithConfirm(result.retryMessage());
                DeliveryOutcome outcome = confirmed ? DeliveryOutcome.ACK : DeliveryOutcome.NACK_REQUEUE;
                applyOutcome(channel, deliveryTag, outcome, result.messageId());
            }
        }
    }

    private static void validateMessage(NotificationMessage message, NotificationChannel expectedChannel) {
        if (message.getMessageId() == null || message.getNotificationId() == null) {
            throw new MalformedMessageException("Missing messageId or notificationId");
        }
        if (message.getChannel() == null || message.getRecipient() == null || message.getMessage() == null) {
            throw new MalformedMessageException("Missing required notification fields");
        }
        if (message.getChannel() != expectedChannel) {
            throw new MalformedMessageException(
                    "Channel mismatch: expected " + expectedChannel + ", got " + message.getChannel()
            );
        }
    }

    private void applyOutcome(Channel channel, long deliveryTag, DeliveryOutcome outcome, String messageId) {
        try {
            deliveryHandler.apply(channel, deliveryTag, outcome, messageId);
        } catch (IOException | IllegalStateException ex) {
            log.warn("Broker ack/nack failed after processing messageId={}: {}", messageId, ex.getMessage());
        }
    }
}