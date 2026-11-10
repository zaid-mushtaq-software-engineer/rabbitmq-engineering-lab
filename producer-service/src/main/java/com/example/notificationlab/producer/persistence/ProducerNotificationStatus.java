package com.example.notificationlab.producer.persistence;

public enum ProducerNotificationStatus {
    PENDING_PUBLISH,
    QUEUED,
    PUBLISH_FAILED,
    PROCESSING,
    RETRY_SCHEDULED,
    SENT,
    DEAD_LETTERED
}
