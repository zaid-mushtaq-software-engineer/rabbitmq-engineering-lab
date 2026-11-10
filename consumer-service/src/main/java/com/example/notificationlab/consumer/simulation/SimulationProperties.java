package com.example.notificationlab.consumer.simulation;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "lab.simulation")
public class SimulationProperties {

    private boolean enabled = true;
    private Duration latencyMin = Duration.ofMillis(100);
    private Duration latencyMax = Duration.ofMillis(500);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Duration getLatencyMin() {
        return latencyMin;
    }

    public void setLatencyMin(Duration latencyMin) {
        this.latencyMin = latencyMin;
    }

    public Duration getLatencyMax() {
        return latencyMax;
    }

    public void setLatencyMax(Duration latencyMax) {
        this.latencyMax = latencyMax;
    }
}
