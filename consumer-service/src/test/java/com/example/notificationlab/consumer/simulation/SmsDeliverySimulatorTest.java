package com.example.notificationlab.consumer.simulation;

import com.example.notificationlab.contracts.NotificationChannel;
import com.example.notificationlab.contracts.NotificationMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SmsDeliverySimulatorTest {

    private SmsDeliverySimulator simulator;

    @BeforeEach
    void setUp() {
        SimulationProperties properties = new SimulationProperties();
        properties.setEnabled(true);
        properties.setLatencyMin(Duration.ZERO);
        properties.setLatencyMax(Duration.ZERO);
        simulator = new SmsDeliverySimulator(properties);
    }

    @Test
    void normalNumberSucceeds() {
        assertThatCode(() -> simulator.deliver(sms("+15550100099", 0))).doesNotThrowAnyException();
    }

    @Test
    void flakySucceedsOnThirdAttempt() {
        assertThatThrownBy(() -> simulator.deliver(sms("+15550100002", 0)))
                .isInstanceOf(TemporaryDeliveryException.class);
        assertThatThrownBy(() -> simulator.deliver(sms("+15550100002", 1)))
                .isInstanceOf(TemporaryDeliveryException.class);
        assertThatCode(() -> simulator.deliver(sms("+15550100002", 2))).doesNotThrowAnyException();
    }

    private static NotificationMessage sms(String recipient, int retryCount) {
        return new NotificationMessage(
                "m1", "n1", NotificationChannel.SMS, recipient, "hi",
                Instant.parse("2026-11-09T10:15:30.123Z"), "c1", retryCount
        );
    }
}
