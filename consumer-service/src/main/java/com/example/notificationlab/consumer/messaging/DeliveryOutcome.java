package com.example.notificationlab.consumer.messaging;

public enum DeliveryOutcome {
    ACK,
    NACK_NO_REQUEUE,
    NACK_REQUEUE
}
