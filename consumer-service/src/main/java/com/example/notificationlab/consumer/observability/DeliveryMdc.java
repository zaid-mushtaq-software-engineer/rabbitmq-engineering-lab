package com.example.notificationlab.consumer.observability;

import com.example.notificationlab.contracts.NotificationChannel;
import com.example.notificationlab.contracts.NotificationMessage;
import com.example.notificationlab.contracts.ObservabilityKeys;
import org.slf4j.MDC;

public final class DeliveryMdc {

    private DeliveryMdc() {
    }

    public static void put(NotificationMessage message) {
        String correlationId = message.getCorrelationId();
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = message.getMessageId();
        }
        MDC.put(ObservabilityKeys.MDC_CORRELATION_ID, correlationId);
        if (message.getNotificationId() != null) {
            MDC.put(ObservabilityKeys.MDC_NOTIFICATION_ID, message.getNotificationId());
        }
        if (message.getMessageId() != null) {
            MDC.put(ObservabilityKeys.MDC_MESSAGE_ID, message.getMessageId());
        }
        NotificationChannel channel = message.getChannel();
        if (channel != null) {
            MDC.put(ObservabilityKeys.MDC_CHANNEL, channel.name());
        }
    }

    public static void putCorrelationOnly(String correlationOrMessageId) {
        if (correlationOrMessageId != null && !correlationOrMessageId.isBlank()) {
            MDC.put(ObservabilityKeys.MDC_CORRELATION_ID, correlationOrMessageId);
        }
    }

    public static void clear() {
        MDC.remove(ObservabilityKeys.MDC_CORRELATION_ID);
        MDC.remove(ObservabilityKeys.MDC_NOTIFICATION_ID);
        MDC.remove(ObservabilityKeys.MDC_MESSAGE_ID);
        MDC.remove(ObservabilityKeys.MDC_CHANNEL);
    }
}