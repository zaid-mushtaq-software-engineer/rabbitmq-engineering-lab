package com.example.notificationlab.consumer.simulation;

import com.example.notificationlab.contracts.NotificationChannel;
import com.example.notificationlab.contracts.NotificationMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailDeliverySimulatorTest {

    private EmailDeliverySimulator simulator;

    @BeforeEach
    void setUp() {
        SimulationProperties properties = new SimulationProperties();
        properties.setEnabled(true);
        properties.setLatencyMin(Duration.ZERO);
        properties.setLatencyMax(Duration.ZERO);
        simulator = new EmailDeliverySimulator(properties);
    }

    @Test
    void normalRecipientSucceeds() {
        assertThatCode(() -> simulator.deliver(message("alice@example.com", 0))).doesNotThrowAnyException();
    }

    @Test
    void tempFailAlwaysThrowsTemporary() {
        assertThatThrownBy(() -> simulator.deliver(message("temp-fail@example.com", 0)))
                .isInstanceOf(TemporaryDeliveryException.class);
    }

    @Test
    void permFailThrowsPermanent() {
        assertThatThrownBy(() -> simulator.deliver(message("perm-fail@example.com", 0)))
                .isInstanceOf(PermanentDeliveryException.class);
    }

    @Test
    void flakyFailsUntilRetryCount2() {
        assertThatThrownBy(() -> simulator.deliver(message("flaky@example.com", 0)))
                .isInstanceOf(TemporaryDeliveryException.class);
        assertThatThrownBy(() -> simulator.deliver(message("flaky@example.com", 1)))
                .isInstanceOf(TemporaryDeliveryException.class);
        assertThatCode(() -> simulator.deliver(message("flaky@example.com", 2))).doesNotThrowAnyException();
    }

    private static NotificationMessage message(String recipient, int retryCount) {
        return new NotificationMessage(
                "m1", "n1", NotificationChannel.EMAIL, recipient, "hi",
                Instant.parse("2026-11-09T10:15:30.123Z"), "c1", retryCount
        );
    }
}
