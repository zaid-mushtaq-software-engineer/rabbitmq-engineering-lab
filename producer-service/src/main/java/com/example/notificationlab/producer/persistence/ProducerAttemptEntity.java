package com.example.notificationlab.producer.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "notification_attempts")
public class ProducerAttemptEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "notification_id", columnDefinition = "CHAR(36)", nullable = false)
    private String notificationId;

    @Column(name = "message_id", columnDefinition = "CHAR(36)", nullable = false)
    private String messageId;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Column(name = "consumer_name", length = 30, nullable = false)
    private String consumerName;

    @Column(name = "outcome", length = 25, nullable = false)
    private String outcome;

    @Column(name = "error_code", length = 50)
    private String errorCode;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "redelivered", nullable = false)
    private boolean redelivered;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at", nullable = false)
    private Instant finishedAt;

    public Long getId() {
        return id;
    }

    public int getAttemptNumber() {
        return attemptNumber;
    }

    public String getConsumerName() {
        return consumerName;
    }

    public String getOutcome() {
        return outcome;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public boolean isRedelivered() {
        return redelivered;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }
}
