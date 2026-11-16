package com.example.notificationlab.consumer.service;

import com.example.notificationlab.consumer.persistence.ProcessedMessageEntity;
import com.example.notificationlab.consumer.persistence.ProcessedMessageRepository;
import com.example.notificationlab.consumer.persistence.ProcessedOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);

    private final ProcessedMessageRepository processedMessageRepository;

    public IdempotencyService(ProcessedMessageRepository processedMessageRepository) {
        this.processedMessageRepository = processedMessageRepository;
    }

    public Optional<ProcessedMessageEntity> findFinalOutcome(String messageId) {
        return processedMessageRepository.findById(messageId);
    }

    public void logDuplicateIgnored(String messageId, ProcessedOutcome outcome, boolean redelivered) {
        log.info("duplicate.ignored messageId={} outcome={} redelivered={}", messageId, outcome, redelivered);
    }

    public void recordFinalOutcome(
            String messageId,
            String notificationId,
            String consumerName,
            ProcessedOutcome outcome,
            Instant processedAt
    ) {
        ProcessedMessageEntity processed = new ProcessedMessageEntity();
        processed.setMessageId(messageId);
        processed.setNotificationId(notificationId);
        processed.setConsumerName(consumerName);
        processed.setOutcome(outcome);
        processed.setProcessedAt(processedAt);
        processedMessageRepository.saveAndFlush(processed);
    }
}