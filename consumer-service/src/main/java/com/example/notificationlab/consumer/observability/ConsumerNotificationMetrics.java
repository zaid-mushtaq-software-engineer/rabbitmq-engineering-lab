package com.example.notificationlab.consumer.observability;

import com.example.notificationlab.contracts.NotificationChannel;
import com.example.notificationlab.consumer.persistence.ProcessedOutcome;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

@Component
public class ConsumerNotificationMetrics {

    private final MeterRegistry registry;

    public ConsumerNotificationMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordProcessed(NotificationChannel channel, ProcessedOutcome outcome) {
        registry.counter(
                "notifications.processed",
                "channel", channel.name(),
                "outcome", outcome.name()
        ).increment();
    }

    public void recordRetryScheduled(NotificationChannel channel) {
        registry.counter("notifications.retry.scheduled", "channel", channel.name()).increment();
    }

    public void recordDuplicate(NotificationChannel channel) {
        registry.counter("notifications.duplicates", "channel", channel.name()).increment();
    }

    public Timer.Sample startProcessingTimer() {
        return Timer.start(registry);
    }

    public void stopProcessingTimer(Timer.Sample sample, NotificationChannel channel) {
        sample.stop(processingTimer(channel));
    }

    private Timer processingTimer(NotificationChannel channel) {
        return Timer.builder("notifications.processing.duration")
                .tag("channel", channel.name())
                .register(registry);
    }
}