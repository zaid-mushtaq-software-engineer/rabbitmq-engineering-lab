package com.example.notificationlab.contracts;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum NotificationChannel {
    @JsonProperty("EMAIL")
    EMAIL,
    @JsonProperty("SMS")
    SMS
}
