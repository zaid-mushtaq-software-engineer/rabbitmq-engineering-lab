package com.example.notificationlab.consumer.persistence;

import com.example.notificationlab.contracts.NotificationChannel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional
class NotificationRepositoryIT {

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
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
    }

    @Autowired
    private NotificationRepository notificationRepository;

  @Test
    void savesAndFindsNotification() {
        NotificationEntity entity = sample("id-1", "msg-1");
        notificationRepository.save(entity);
        assertThat(notificationRepository.findByMessageId("msg-1")).isPresent();
    }

    @Test
    void rejectsDuplicateMessageId() {
        notificationRepository.save(sample("id-1", "msg-dup"));
        assertThatThrownBy(() -> notificationRepository.save(sample("id-2", "msg-dup")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private static NotificationEntity sample(String id, String messageId) {
        Instant now = Instant.parse("2026-11-09T10:15:30.123Z");
        NotificationEntity entity = new NotificationEntity();
        entity.setId(id);
        entity.setMessageId(messageId);
        entity.setChannel(NotificationChannel.EMAIL);
        entity.setRecipient("a@example.com");
        entity.setMessage("hello");
        entity.setStatus(NotificationStatus.QUEUED);
        entity.setCorrelationId("c1");
        entity.setRetryCount(0);
        entity.setAttemptCount(0);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity.setVersion(0L);
        return entity;
    }
}
