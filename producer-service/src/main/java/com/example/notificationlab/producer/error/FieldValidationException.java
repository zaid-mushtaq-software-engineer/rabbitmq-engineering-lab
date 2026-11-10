package com.example.notificationlab.producer.error;

public class FieldValidationException extends RuntimeException {

    private final String field;
    private final String reason;

    public FieldValidationException(String field, String reason) {
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
