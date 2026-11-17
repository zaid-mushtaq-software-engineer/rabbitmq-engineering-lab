package com.example.notificationlab.producer.observability;

import com.example.notificationlab.producer.persistence.ProducerNotificationRepository;
import com.example.notificationlab.producer.persistence.ProducerNotificationStatus;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
public class StuckPendingPublishMetrics {

    private final ProducerNotificationRepository repository;
    private final Duration stuckThreshold;

    public StuckPendingPublishMetrics(
            ProducerNotificationRepository repository,
            MeterRegistry registry,
            @Value("${lab.observability.stuck-pending-threshold:5m}") Duration stuckThreshold
    ) {
        this.repository = repository;
        this.stuckThreshold = stuckThreshold;
        Gauge.builder("notifications.pending_publish.stuck", this::countStuck)
                .description("Notifications in PENDING_PUBLISH longer than the configured threshold")
                .register(registry);
    }

    double countStuck() {
        Instant before = Instant.now().minus(stuckThreshold);
        return repository.countByStatusAndCreatedAtBefore(ProducerNotificationStatus.PENDING_PUBLISH, before);
    }
}