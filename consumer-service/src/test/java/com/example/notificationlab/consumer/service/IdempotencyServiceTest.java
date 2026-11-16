package com.example.notificationlab.consumer.service;

import com.example.notificationlab.consumer.persistence.ProcessedMessageEntity;
import com.example.notificationlab.consumer.persistence.ProcessedMessageRepository;
import com.example.notificationlab.consumer.persistence.ProcessedOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    @Mock
    private ProcessedMessageRepository processedMessageRepository;

    @InjectMocks
    private IdempotencyService idempotencyService;

    @Test
    void findFinalOutcomeDelegatesToRepository() {
        ProcessedMessageEntity row = new ProcessedMessageEntity();
        row.setMessageId("m1");
        row.setOutcome(ProcessedOutcome.SENT);
        when(processedMessageRepository.findById("m1")).thenReturn(Optional.of(row));

        assertThat(idempotencyService.findFinalOutcome("m1"))
                .isPresent()
                .get()
                .extracting(ProcessedMessageEntity::getOutcome)
                .isEqualTo(ProcessedOutcome.SENT);
    }

    @Test
    void recordFinalOutcomePersistsRow() {
        Instant at = Instant.parse("2026-11-09T10:00:00Z");
        idempotencyService.recordFinalOutcome("m2", "n2", "sms-consumer", ProcessedOutcome.DEAD_LETTERED, at);

        verify(processedMessageRepository).saveAndFlush(org.mockito.ArgumentMatchers.argThat(entity ->
                "m2".equals(entity.getMessageId())
                        && entity.getOutcome() == ProcessedOutcome.DEAD_LETTERED
                        && "sms-consumer".equals(entity.getConsumerName())
        ));
    }
}