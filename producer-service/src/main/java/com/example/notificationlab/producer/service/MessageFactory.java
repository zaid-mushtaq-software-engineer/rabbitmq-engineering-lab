package com.example.notificationlab.producer.service;

import com.example.notificationlab.contracts.NotificationChannel;
import com.example.notificationlab.contracts.NotificationMessage;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class MessageFactory {

    public BuiltNotification build(
            NotificationChannel channel,
            String recipient,
            String messageText,
            String correlationId
    ) {
        String notificationId = UUID.randomUUID().toString();
        String messageId = UUID.randomUUID().toString();
        String effectiveCorrelationId = (correlationId == null || correlationId.isBlank())
                ? "req-" + UUID.randomUUID()
                : correlationId;
        NotificationMessage message = new NotificationMessage(
                messageId,
                notificationId,
                channel,
                recipient,
                messageText,
                Instant.now(),
                effectiveCorrelationId,
                0
        );
        return new BuiltNotification(notificationId, messageId, effectiveCorrelationId, message);
    }

    public record BuiltNotification(
            String notificationId,
            String messageId,
            String correlationId,
            NotificationMessage message
    ) {
    }
}
