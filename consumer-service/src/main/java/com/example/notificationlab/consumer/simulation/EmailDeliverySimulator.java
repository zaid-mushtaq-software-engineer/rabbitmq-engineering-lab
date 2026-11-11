package com.example.notificationlab.consumer.simulation;

import com.example.notificationlab.contracts.NotificationMessage;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class EmailDeliverySimulator {

    private static final String TEMP_FAIL = "temp-fail@example.com";
    private static final String FLAKY = "flaky@example.com";
    private static final String PERM_FAIL = "perm-fail@example.com";
    private static final String SLOW = "slow@example.com";

    private final SimulationProperties properties;

    public EmailDeliverySimulator(SimulationProperties properties) {
        this.properties = properties;
    }

    public void deliver(NotificationMessage message) {
        if (!properties.isEnabled()) {
            return;
        }
        String recipient = message.getRecipient();
        if (TEMP_FAIL.equalsIgnoreCase(recipient)) {
            throw new TemporaryDeliveryException("Simulated provider timeout");
        }
        if (PERM_FAIL.equalsIgnoreCase(recipient)) {
            throw new PermanentDeliveryException("recipient rejected by provider (simulated)");
        }
        if (FLAKY.equalsIgnoreCase(recipient) && message.getRetryCount() < 2) {
            throw new TemporaryDeliveryException("Simulated provider timeout");
        }
        sleep(recipient);
    }

    private void sleep(String recipient) {
        Duration delay = SLOW.equalsIgnoreCase(recipient)
                ? properties.getSlowDelay()
                : randomLatency();
        if (!delay.isZero()) {
            try {
                Thread.sleep(delay.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new TemporaryDeliveryException("Interrupted during simulated latency");
            }
        }
    }

    private Duration randomLatency() {
        long min = properties.getLatencyMin().toMillis();
        long max = properties.getLatencyMax().toMillis();
        if (max <= min) {
            return Duration.ofMillis(min);
        }
        long span = max - min;
        return Duration.ofMillis(min + (long) (Math.random() * span));
    }
}
