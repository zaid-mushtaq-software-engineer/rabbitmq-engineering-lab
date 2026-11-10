package com.example.notificationlab.producer.api.dto;

import com.example.notificationlab.producer.api.validation.ValidNotificationRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@ValidNotificationRequest
public class SubmitNotificationRequest {

    @NotNull(message = "must not be null")
    private String channel;

    @NotBlank(message = "must not be blank")
    @Size(max = 254)
    private String recipient;

    @NotBlank(message = "must not be blank")
    private String message;

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getRecipient() {
        return recipient;
    }

    public void setRecipient(String recipient) {
        this.recipient = recipient;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
