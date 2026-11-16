package com.example.notificationlab.producer.messaging;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PublishResultTest {

    @Test
    void confirmedWhenOutcomeIsConfirmed() {
        assertThat(PublishResult.confirmed().isConfirmed()).isTrue();
    }

    @Test
    void failureReasonsMatchApiContract() {
        assertThat(new PublishResult(PublishOutcome.TIMED_OUT, null).apiFailureReason())
                .contains("confirm");
        assertThat(new PublishResult(PublishOutcome.RETURNED_UNROUTABLE, null).apiFailureReason())
                .contains("No queue");
        assertThat(new PublishResult(PublishOutcome.NACKED, null).apiFailureReason())
                .contains("nacked");
    }
}