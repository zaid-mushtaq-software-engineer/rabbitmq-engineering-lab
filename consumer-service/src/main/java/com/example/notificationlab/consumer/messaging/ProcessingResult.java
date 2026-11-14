package com.example.notificationlab.consumer.messaging;

import com.example.notificationlab.contracts.NotificationMessage;

public final class ProcessingResult {

    public enum Step {
        ACK,
        NACK_NO_REQUEUE,
        PUBLISH_RETRY_THEN_ACK
    }

    private final Step step;
    private final String messageId;
    private final NotificationMessage retryMessage;

    private ProcessingResult(Step step, String messageId, NotificationMessage retryMessage) {
        this.step = step;
        this.messageId = messageId;
        this.retryMessage = retryMessage;
    }

    public static ProcessingResult ack(String messageId) {
        return new ProcessingResult(Step.ACK, messageId, null);
    }

    public static ProcessingResult nackNoRequeue(String messageId) {
        return new ProcessingResult(Step.NACK_NO_REQUEUE, messageId, null);
    }

    public static ProcessingResult publishRetryThenAck(String messageId, NotificationMessage retryMessage) {
        return new ProcessingResult(Step.PUBLISH_RETRY_THEN_ACK, messageId, retryMessage);
    }

    public Step step() {
        return step;
    }

    public String messageId() {
        return messageId;
    }

    public NotificationMessage retryMessage() {
        return retryMessage;
    }
}