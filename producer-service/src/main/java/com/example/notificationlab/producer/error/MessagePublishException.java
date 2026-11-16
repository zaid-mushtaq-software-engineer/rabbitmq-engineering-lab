package com.example.notificationlab.producer.error;

public class MessagePublishException extends RuntimeException {

    private final String notificationId;
    private final String failureReason;

    public MessagePublishException(String notificationId, String failureReason) {
        super(failureReason);
        this.notificationId = notificationId;
        this.failureReason = failureReason;
    }

    public String getNotificationId() {
        return notificationId;
    }

    public String getFailureReason() {
        return failureReason;
    }
}