package com.example.notificationlab.contracts;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public final class NotificationMessage {

    private String messageId;
    private String notificationId;
    private NotificationChannel channel;
    private String recipient;
    private String message;
    private Instant createdAt;
    private String correlationId;
    private int retryCount;

    public NotificationMessage() {
    }

    public NotificationMessage(
            String messageId,
            String notificationId,
            NotificationChannel channel,
            String recipient,
            String message,
            Instant createdAt,
            String correlationId,
            int retryCount
    ) {
        this.messageId = messageId;
        this.notificationId = notificationId;
        this.channel = channel;
        this.recipient = recipient;
        this.message = message;
        this.createdAt = createdAt;
        this.correlationId = correlationId;
        this.retryCount = retryCount;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getNotificationId() {
        return notificationId;
    }

    public void setNotificationId(String notificationId) {
        this.notificationId = notificationId;
    }

    public NotificationChannel getChannel() {
        return channel;
    }

    public void setChannel(NotificationChannel channel) {
        this.channel = channel;
    }

    public String getRecipient() {
        return recipient;
    }

    public void setRecipient(String recipient) {
        this.recipient = recipient;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        NotificationMessage that = (NotificationMessage) o;
        return retryCount == that.retryCount
                && Objects.equals(messageId, that.messageId)
                && Objects.equals(notificationId, that.notificationId)
                && channel == that.channel
                && Objects.equals(recipient, that.recipient)
                && Objects.equals(message, that.message)
                && Objects.equals(createdAt, that.createdAt)
                && Objects.equals(correlationId, that.correlationId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(messageId, notificationId, channel, recipient, message, createdAt, correlationId, retryCount);
    }
}
