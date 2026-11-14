package com.example.notificationlab.consumer.messaging;

import com.example.notificationlab.consumer.simulation.PermanentDeliveryException;
import com.example.notificationlab.consumer.simulation.TemporaryDeliveryException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FailureClassifierTest {

    private final FailureClassifier classifier = new FailureClassifier();

    @Test
    void classifiesTemporaryDelivery() {
        assertThat(classifier.classify(new TemporaryDeliveryException("timeout")))
                .isEqualTo(FailureClassification.TEMPORARY);
    }

    @Test
    void classifiesPermanentDelivery() {
        assertThat(classifier.classify(new PermanentDeliveryException("rejected")))
                .isEqualTo(FailureClassification.PERMANENT);
    }

    @Test
    void classifiesMalformedAsPermanent() {
        assertThat(classifier.classify(new MalformedMessageException("bad json")))
                .isEqualTo(FailureClassification.PERMANENT);
    }

    @Test
    void classifiesUnknownAsTemporary() {
        assertThat(classifier.classify(new IllegalStateException("db down")))
                .isEqualTo(FailureClassification.TEMPORARY);
    }
}