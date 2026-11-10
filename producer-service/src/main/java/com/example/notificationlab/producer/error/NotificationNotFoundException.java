package com.example.notificationlab.producer.error;

public class NotificationNotFoundException extends RuntimeException {

    public NotificationNotFoundException(String notificationId) {
        super("Notification " + notificationId + " was not found.");
    }
}
