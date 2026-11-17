package com.example.notificationlab.producer.observability;

import com.example.notificationlab.contracts.NotificationChannel;
import com.example.notificationlab.producer.messaging.PublishOutcome;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class ProducerNotificationMetrics {

    private final MeterRegistry registry;

    public ProducerNotificationMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordSubmitted(NotificationChannel channel) {
        registry.counter("notifications.submitted", "channel", channel.name()).increment();
    }

    public void recordPublishResult(PublishOutcome outcome) {
        registry.counter("notifications.publish.result", "result", outcome.name()).increment();
    }
}