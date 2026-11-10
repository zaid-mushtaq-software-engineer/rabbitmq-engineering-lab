package com.example.notificationlab.producer.persistence;

import com.example.notificationlab.contracts.NotificationChannel;
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
class ProducerNotificationRepositoryIT {

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
    }

    @Autowired
    private ProducerNotificationRepository repository;

    @Test
    @Transactional
    void markQueuedOnlyFromPendingPublish() {
        String id = UUID.randomUUID().toString();
        Instant now = Instant.now();
        repository.save(sample(id, ProducerNotificationStatus.PENDING_PUBLISH));

        int updated = repository.markQueuedIfPendingPublish(id, now, now);
        assertThat(updated).isEqualTo(1);
        assertThat(repository.findById(id).orElseThrow().getStatus()).isEqualTo(ProducerNotificationStatus.QUEUED);

        int second = repository.markQueuedIfPendingPublish(id, now, now);
        assertThat(second).isZero();
    }

    @Test
    @Transactional
    void doesNotOverwriteProcessing() {
        String id = UUID.randomUUID().toString();
        Instant now = Instant.now();
        repository.save(sample(id, ProducerNotificationStatus.PROCESSING));

        int updated = repository.markQueuedIfPendingPublish(id, now, now);
        assertThat(updated).isZero();
        assertThat(repository.findById(id).orElseThrow().getStatus()).isEqualTo(ProducerNotificationStatus.PROCESSING);
    }

    private static ProducerNotificationEntity sample(String id, ProducerNotificationStatus status) {
        Instant now = Instant.parse("2026-11-09T10:15:30.123Z");
        ProducerNotificationEntity entity = new ProducerNotificationEntity();
        entity.setId(id);
        entity.setMessageId(UUID.randomUUID().toString());
        entity.setChannel(NotificationChannel.EMAIL);
        entity.setRecipient("a@example.com");
        entity.setMessage("hello");
        entity.setStatus(status);
        entity.setCorrelationId("c1");
        entity.setRetryCount(0);
        entity.setAttemptCount(0);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity.setVersion(0L);
        return entity;
    }
}
