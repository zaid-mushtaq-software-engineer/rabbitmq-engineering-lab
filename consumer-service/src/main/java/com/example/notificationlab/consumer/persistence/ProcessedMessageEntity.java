package com.example.notificationlab.consumer.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "processed_messages")
public class ProcessedMessageEntity {

    @Id
    @Column(name = "message_id", columnDefinition = "CHAR(36)")
    private String messageId;

    @Column(name = "notification_id", columnDefinition = "CHAR(36)", nullable = false)
    private String notificationId;

    @Column(name = "consumer_name", length = 30, nullable = false)
    private String consumerName;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", length = 20, nullable = false)
    private ProcessedOutcome outcome;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

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

    public String getConsumerName() {
        return consumerName;
    }

    public void setConsumerName(String consumerName) {
        this.consumerName = consumerName;
    }

    public ProcessedOutcome getOutcome() {
        return outcome;
    }

    public void setOutcome(ProcessedOutcome outcome) {
        this.outcome = outcome;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(Instant processedAt) {
        this.processedAt = processedAt;
    }
}