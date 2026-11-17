package com.example.notificationlab.contracts;

/**
 * Shared SLF4J MDC key names for cross-service log correlation.
 */
public final class ObservabilityKeys {

    public static final String MDC_CORRELATION_ID = "correlationId";
    public static final String MDC_NOTIFICATION_ID = "notificationId";
    public static final String MDC_MESSAGE_ID = "messageId";
    public static final String MDC_CHANNEL = "channel";

    private ObservabilityKeys() {
    }
}