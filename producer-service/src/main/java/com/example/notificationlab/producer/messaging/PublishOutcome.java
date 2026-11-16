package com.example.notificationlab.producer.messaging;

public enum PublishOutcome {
    CONFIRMED,
    NACKED,
    TIMED_OUT,
    RETURNED_UNROUTABLE,
    BROKER_UNAVAILABLE
}