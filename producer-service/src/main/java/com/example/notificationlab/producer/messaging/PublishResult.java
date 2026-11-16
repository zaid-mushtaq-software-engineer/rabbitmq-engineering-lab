package com.example.notificationlab.producer.messaging;

public final class PublishResult {

    private final PublishOutcome outcome;
    private final String detail;

    public PublishResult(PublishOutcome outcome, String detail) {
        this.outcome = outcome;
        this.detail = detail != null ? detail : outcome.name();
    }

    public static PublishResult confirmed() {
        return new PublishResult(PublishOutcome.CONFIRMED, null);
    }

    public static PublishResult of(PublishOutcome outcome, String detail) {
        return new PublishResult(outcome, detail);
    }

    public PublishOutcome outcome() {
        return outcome;
    }

    public String detail() {
        return detail;
    }

    public boolean isConfirmed() {
        return outcome == PublishOutcome.CONFIRMED;
    }

    public String apiFailureReason() {
        return switch (outcome) {
            case NACKED -> "Broker nacked the publish";
            case TIMED_OUT -> "Broker did not confirm the message within the configured timeout";
            case RETURNED_UNROUTABLE -> "No queue was bound for the routing key";
            case BROKER_UNAVAILABLE -> detail;
            case CONFIRMED -> detail;
        };
    }
}