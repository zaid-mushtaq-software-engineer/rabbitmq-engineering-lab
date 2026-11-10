package com.example.notificationlab.producer.service;

import com.example.notificationlab.producer.persistence.ProducerNotificationStatus;
import org.springframework.stereotype.Component;

@Component
public class StatusDescriptionMapper {

    public String describe(ProducerNotificationStatus status) {
        return switch (status) {
            case PENDING_PUBLISH -> "Stored; broker confirmation not yet received.";
            case QUEUED -> "Queued. Delivery has not yet been attempted.";
            case PUBLISH_FAILED -> "Could not be queued with the broker.";
            case PROCESSING -> "A consumer is processing an attempt.";
            case RETRY_SCHEDULED -> "Temporary failure; waiting for the next attempt.";
            case SENT -> "Delivered (simulated).";
            case DEAD_LETTERED -> "Could not be delivered; message dead-lettered.";
        };
    }
}
