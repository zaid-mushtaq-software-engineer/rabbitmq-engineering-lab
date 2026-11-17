package com.example.notificationlab.producer.observability;

import com.example.notificationlab.contracts.NotificationChannel;
import com.example.notificationlab.producer.persistence.ProducerNotificationEntity;
import com.example.notificationlab.producer.persistence.ProducerNotificationRepository;
import com.example.notificationlab.producer.persistence.ProducerNotificationStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class StuckPendingPublishMetricsTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("notification_lab")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("lab.observability.stuck-pending-threshold", () -> "1m");
    }

    @Autowired
    private ProducerNotificationRepository repository;

    @Test
    @Transactional
    void gaugeReflectsOldPendingPublishRow() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        StuckPendingPublishMetrics metrics = new StuckPendingPublishMetrics(
                repository, meterRegistry, java.time.Duration.ofMinutes(1)
        );

        String id = UUID.randomUUID().toString();
        ProducerNotificationEntity entity = new ProducerNotificationEntity();
        entity.setId(id);
        entity.setMessageId(UUID.randomUUID().toString());
        entity.setChannel(NotificationChannel.EMAIL);
        entity.setRecipient("a@example.com");
        entity.setMessage("hello");
        entity.setStatus(ProducerNotificationStatus.PENDING_PUBLISH);
        entity.setCorrelationId("c1");
        entity.setRetryCount(0);
        entity.setAttemptCount(0);
        entity.setCreatedAt(Instant.parse("2020-01-01T00:00:00Z"));
        entity.setUpdatedAt(Instant.now());
        entity.setVersion(0L);
        this.repository.save(entity);

        assertThat(metrics.countStuck()).isEqualTo(1.0);
        assertThat(meterRegistry.get("notifications.pending_publish.stuck").gauge().value()).isEqualTo(1.0);
    }
}