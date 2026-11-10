package com.example.notificationlab.producer.error;

public class InvalidPaginationException extends RuntimeException {

    private final String field;
    private final String reason;

    public InvalidPaginationException(String field, String reason) {
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
