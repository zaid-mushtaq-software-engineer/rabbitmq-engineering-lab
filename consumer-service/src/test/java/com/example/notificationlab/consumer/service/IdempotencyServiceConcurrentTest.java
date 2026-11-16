package com.example.notificationlab.consumer.service;

import com.example.notificationlab.contracts.NotificationChannel;
import com.example.notificationlab.consumer.persistence.NotificationEntity;
import com.example.notificationlab.consumer.persistence.NotificationRepository;
import com.example.notificationlab.consumer.persistence.NotificationStatus;
import com.example.notificationlab.consumer.persistence.ProcessedMessageRepository;
import com.example.notificationlab.consumer.persistence.ProcessedOutcome;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T-7.5 — primary key on {@code processed_messages} serialises concurrent winners (see implementation.md 11.4, OQ-04).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class IdempotencyServiceConcurrentTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("notification_lab")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
    }

    @Autowired
    private IdempotencyService idempotencyService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private ProcessedMessageRepository processedMessageRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @RepeatedTest(50)
    void parallelInsertYieldsSingleRow() throws InterruptedException {
        String messageId = UUID.randomUUID().toString();
        String notificationId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        saveNotification(notificationId, messageId, now);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger duplicates = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);

        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 2; i++) {
                pool.submit(() -> {
                    try {
                        start.await();
                        TransactionTemplate tx = new TransactionTemplate(transactionManager);
                        tx.executeWithoutResult(status -> idempotencyService.recordFinalOutcome(
                                messageId,
                                notificationId,
                                "email-consumer",
                                ProcessedOutcome.SENT,
                                now
                        ));
                        successes.incrementAndGet();
                    } catch (Exception ex) {
                        if (isDuplicateKey(ex)) {
                            duplicates.incrementAndGet();
                        } else {
                            throw new RuntimeException(ex);
                        }
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            done.await();
        }

        assertThat(successes.get() + duplicates.get()).isEqualTo(2);
        assertThat(successes.get()).isEqualTo(1);
        assertThat(duplicates.get()).isEqualTo(1);
        assertThat(processedMessageRepository.findById(messageId)).isPresent();
    }

    private void saveNotification(String id, String messageId, Instant now) {
        NotificationEntity entity = new NotificationEntity();
        entity.setId(id);
        entity.setMessageId(messageId);
        entity.setChannel(NotificationChannel.EMAIL);
        entity.setRecipient("a@example.com");
        entity.setMessage("concurrent-idempotency");
        entity.setStatus(NotificationStatus.QUEUED);
        entity.setCorrelationId("c1");
        entity.setRetryCount(0);
        entity.setAttemptCount(0);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity.setVersion(0L);
        notificationRepository.saveAndFlush(entity);
    }

    private static boolean isDuplicateKey(Throwable ex) {
        while (ex != null) {
            if (ex instanceof DataIntegrityViolationException) {
                return true;
            }
            ex = ex.getCause();
        }
        return false;
    }
}