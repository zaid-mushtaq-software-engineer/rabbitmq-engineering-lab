package com.example.notificationlab.consumer.persistence;

public enum NotificationStatus {
    PENDING_PUBLISH,
    QUEUED,
    PUBLISH_FAILED,
    PROCESSING,
    RETRY_SCHEDULED,
    SENT,
    DEAD_LETTERED
}
