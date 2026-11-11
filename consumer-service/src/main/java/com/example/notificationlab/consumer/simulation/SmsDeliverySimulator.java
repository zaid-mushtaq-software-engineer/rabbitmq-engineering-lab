package com.example.notificationlab.consumer.simulation;

import com.example.notificationlab.contracts.NotificationMessage;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class SmsDeliverySimulator {

    private static final String TEMP_FAIL = "+15550100001";
    private static final String FLAKY = "+15550100002";
    private static final String PERM_FAIL = "+15550100003";
    private static final String SLOW = "+15550100004";

    private final SimulationProperties properties;

    public SmsDeliverySimulator(SimulationProperties properties) {
        this.properties = properties;
    }

    public void deliver(NotificationMessage message) {
        if (!properties.isEnabled()) {
            return;
        }
        String recipient = message.getRecipient();
        if (TEMP_FAIL.equals(recipient)) {
            throw new TemporaryDeliveryException("Simulated provider timeout");
        }
        if (PERM_FAIL.equals(recipient)) {
            throw new PermanentDeliveryException("recipient rejected by provider (simulated)");
        }
        if (FLAKY.equals(recipient) && message.getRetryCount() < 2) {
            throw new TemporaryDeliveryException("Simulated provider timeout");
        }
        sleep(recipient);
    }

    private void sleep(String recipient) {
        Duration delay = SLOW.equals(recipient)
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
        return Duration.ofMillis(min + (long) (Math.random() * (max - min)));
    }
}
