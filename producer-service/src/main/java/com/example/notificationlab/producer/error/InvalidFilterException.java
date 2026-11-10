package com.example.notificationlab.producer.error;

public class InvalidFilterException extends RuntimeException {

    private final String field;
    private final String reason;

    public InvalidFilterException(String field, String reason) {
        super(reason);
        this.field = field;
        this.reason = reason;
    }

    public String getField() {
        return field;
    }

    public String getReason() {
        return reason;
    }
}
