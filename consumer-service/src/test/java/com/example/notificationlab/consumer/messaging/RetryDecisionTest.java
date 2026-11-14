package com.example.notificationlab.consumer.messaging;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class RetryDecisionTest {

    @ParameterizedTest
    @CsvSource({
            "0, 3, PERMANENT, DEAD_LETTER",
            "0, 3, TEMPORARY, RETRY",
            "2, 3, TEMPORARY, RETRY",
            "3, 3, TEMPORARY, DEAD_LETTER",
            "0, 0, TEMPORARY, DEAD_LETTER",
            "5, 3, TEMPORARY, DEAD_LETTER"
    })
    void decidesRetryOrDeadLetter(int retryCount, int maxRetries, String classification, String expected) {
        FailureClassification failure = FailureClassification.valueOf(classification);
        RetryAction action = RetryDecision.decide(retryCount, maxRetries, failure);
        assertThat(action).isEqualTo(RetryAction.valueOf(expected));
    }
}