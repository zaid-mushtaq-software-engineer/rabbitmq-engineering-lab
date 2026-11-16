package com.example.notificationlab.consumer.service;

import com.example.notificationlab.contracts.NotificationChannel;
import com.example.notificationlab.contracts.NotificationMessage;
import com.example.notificationlab.consumer.messaging.FailureClassification;
import com.example.notificationlab.consumer.messaging.FailureClassifier;
import com.example.notificationlab.consumer.messaging.ProcessingResult;
import com.example.notificationlab.consumer.messaging.RetryAction;
import com.example.notificationlab.consumer.messaging.RetryDecision;
import com.example.notificationlab.consumer.persistence.AttemptOutcome;
import com.example.notificationlab.consumer.persistence.NotificationAttemptEntity;
import com.example.notificationlab.consumer.persistence.NotificationAttemptRepository;
import com.example.notificationlab.consumer.persistence.NotificationEntity;
import com.example.notificationlab.consumer.persistence.NotificationRepository;
import com.example.notificationlab.consumer.persistence.NotificationStatus;
import com.example.notificationlab.consumer.persistence.ProcessedMessageEntity;
import com.example.notificationlab.consumer.persistence.ProcessedOutcome;
import com.example.notificationlab.consumer.simulation.EmailDeliverySimulator;
import com.example.notificationlab.consumer.simulation.PermanentDeliveryException;
import com.example.notificationlab.consumer.simulation.SmsDeliverySimulator;
import com.example.notificationlab.consumer.simulation.TemporaryDeliveryException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class NotificationProcessingService {

    private static final Logger log = LoggerFactory.getLogger(NotificationProcessingService.class);

    private final NotificationRepository notificationRepository;
    private final NotificationAttemptRepository attemptRepository;
    private final IdempotencyService idempotencyService;
    private final EmailDeliverySimulator emailDeliverySimulator;
    private final SmsDeliverySimulator smsDeliverySimulator;
    private final FailureClassifier failureClassifier;
    private final TransactionTemplate transactionTemplate;
    private final int maxRetries;

    public NotificationProcessingService(
            NotificationRepository notificationRepository,
            NotificationAttemptRepository attemptRepository,
            IdempotencyService idempotencyService,
            EmailDeliverySimulator emailDeliverySimulator,
            SmsDeliverySimulator smsDeliverySimulator,
            FailureClassifier failureClassifier,
            PlatformTransactionManager transactionManager,
            @Value("${lab.retry.max-retries:3}") int maxRetries
    ) {
        this.notificationRepository = notificationRepository;
        this.attemptRepository = attemptRepository;
        this.idempotencyService = idempotencyService;
        this.emailDeliverySimulator = emailDeliverySimulator;
        this.smsDeliverySimulator = smsDeliverySimulator;
        this.failureClassifier = failureClassifier;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.maxRetries = maxRetries;
    }

    public ProcessingResult process(
            NotificationMessage message,
            boolean redelivered,
            NotificationChannel expectedChannel,
            String consumerName
    ) {
        Optional<ProcessedMessageEntity> existing = idempotencyService.findFinalOutcome(message.getMessageId());
        if (existing.isPresent()) {
            return duplicateIgnoredOutcome(existing.get().getOutcome(), message.getMessageId(), redelivered);
        }

        Instant now = Instant.now();
        NotificationEntity notification;
        try {
            notification = beginAttempt(message, now);
        } catch (DuplicateOutcomeException ex) {
            return duplicateIgnoredOutcome(ex.outcome(), message.getMessageId(), redelivered);
        }

        try {
            deliver(message);
            return commitSuccess(notification, message, Instant.now(), redelivered, consumerName);
        } catch (TemporaryDeliveryException | PermanentDeliveryException ex) {
            return handleDeliveryFailure(notification, message, Instant.now(), redelivered, consumerName, ex);
        }
    }

    public ProcessingResult recordMalformedDeadLetter(
            NotificationMessage partial,
            NotificationChannel expectedChannel,
            String consumerName,
            String reason
    ) {
        final String messageId = partial.getMessageId() == null || partial.getMessageId().isBlank()
                ? UUID.randomUUID().toString()
                : partial.getMessageId();

        Optional<ProcessedMessageEntity> existing = idempotencyService.findFinalOutcome(messageId);
        if (existing.isPresent()) {
            return duplicateIgnoredOutcome(existing.get().getOutcome(), messageId, false);
        }

        Instant now = Instant.now();
        try {
            return transactionTemplate.execute(status -> {
                final String notificationId = partial.getNotificationId() != null && !partial.getNotificationId().isBlank()
                        ? partial.getNotificationId()
                        : messageId;

                NotificationEntity notification = notificationRepository
                        .findByMessageId(messageId)
                        .orElseGet(() -> {
                            NotificationEntity entity = new NotificationEntity();
                            entity.setId(notificationId);
                            entity.setMessageId(messageId);
                            entity.setChannel(partial.getChannel() != null ? partial.getChannel() : expectedChannel);
                            entity.setRecipient(partial.getRecipient() != null ? partial.getRecipient() : "unknown");
                            entity.setMessage(partial.getMessage() != null ? partial.getMessage() : "");
                            entity.setStatus(NotificationStatus.QUEUED);
                            entity.setCorrelationId(
                                    partial.getCorrelationId() != null ? partial.getCorrelationId() : messageId
                            );
                            entity.setRetryCount(0);
                            entity.setAttemptCount(0);
                            entity.setCreatedAt(now);
                            entity.setUpdatedAt(now);
                            entity.setVersion(0L);
                            return notificationRepository.save(entity);
                        });

                applyDeadLetterOutcome(
                        notification,
                        partial,
                        now,
                        false,
                        consumerName,
                        AttemptOutcome.PERMANENT_FAILURE,
                        reason
                );
                return ProcessingResult.nackNoRequeue(messageId);
            });
        } catch (DataIntegrityViolationException ex) {
            return duplicateAfterRace(messageId, false);
        }
    }

    private NotificationEntity beginAttempt(NotificationMessage message, Instant now) {
        return transactionTemplate.execute(status -> {
            Optional<ProcessedMessageEntity> existing =
                    idempotencyService.findFinalOutcome(message.getMessageId());
            if (existing.isPresent()) {
                throw new DuplicateOutcomeException(existing.get().getOutcome());
            }

            NotificationEntity notification = notificationRepository
                    .findByMessageId(message.getMessageId())
                    .orElseGet(() -> createNotification(message, now));

            notification.setStatus(NotificationStatus.PROCESSING);
            notification.setUpdatedAt(now);
            return notificationRepository.save(notification);
        });
    }

    private ProcessingResult commitSuccess(
            NotificationEntity notification,
            NotificationMessage message,
            Instant now,
            boolean redelivered,
            String consumerName
    ) {
        try {
            return transactionTemplate.execute(status -> {
                applySuccessOutcome(notification, message, now, redelivered, consumerName);
                return ProcessingResult.ack(message.getMessageId());
            });
        } catch (DataIntegrityViolationException ex) {
            return duplicateAfterRace(message.getMessageId(), redelivered);
        } catch (DuplicateOutcomeException ex) {
            return duplicateIgnoredOutcome(ex.outcome(), message.getMessageId(), redelivered);
        }
    }

    private ProcessingResult handleDeliveryFailure(
            NotificationEntity notification,
            NotificationMessage message,
            Instant now,
            boolean redelivered,
            String consumerName,
            RuntimeException ex
    ) {
        FailureClassification classification = failureClassifier.classify(ex);
        RetryAction action = RetryDecision.decide(message.getRetryCount(), maxRetries, classification);

        if (action == RetryAction.RETRY) {
            transactionTemplate.executeWithoutResult(status -> {
                int attemptNumber = notification.getAttemptCount() + 1;
                recordAttempt(notification, message, attemptNumber, AttemptOutcome.TEMPORARY_FAILURE,
                        ex.getMessage(), redelivered, consumerName, now);

                notification.setStatus(NotificationStatus.RETRY_SCHEDULED);
                notification.setRetryCount(message.getRetryCount() + 1);
                notification.setAttemptCount(attemptNumber);
                notification.setLastError(ex.getMessage());
                notification.setUpdatedAt(now);
                notificationRepository.save(notification);
            });

            NotificationMessage retryCopy = copyForRetry(message);
            log.info("retry.scheduled messageId={} retryCount={}", message.getMessageId(), retryCopy.getRetryCount());
            return ProcessingResult.publishRetryThenAck(message.getMessageId(), retryCopy);
        }

        String errorMessage = classification == FailureClassification.PERMANENT
                ? ex.getMessage()
                : exhaustedRetriesMessage();
        AttemptOutcome attemptOutcome = classification == FailureClassification.PERMANENT
                ? AttemptOutcome.PERMANENT_FAILURE
                : AttemptOutcome.TEMPORARY_FAILURE;

        try {
            return transactionTemplate.execute(status -> {
                applyDeadLetterOutcome(
                        notification, message, now, redelivered, consumerName, attemptOutcome, errorMessage
                );
                log.warn("message.dead_lettered messageId={} reason={}", message.getMessageId(), errorMessage);
                return ProcessingResult.nackNoRequeue(message.getMessageId());
            });
        } catch (DataIntegrityViolationException ex2) {
            return duplicateAfterRace(message.getMessageId(), redelivered);
        }
    }

    private ProcessingResult duplicateIgnoredOutcome(ProcessedOutcome outcome, String messageId, boolean redelivered) {
        idempotencyService.logDuplicateIgnored(messageId, outcome, redelivered);
        if (outcome == ProcessedOutcome.SENT) {
            return ProcessingResult.duplicateAck(messageId);
        }
        return ProcessingResult.duplicateNackNoRequeue(messageId);
    }

    private ProcessingResult duplicateAfterRace(String messageId, boolean redelivered) {
        ProcessedMessageEntity winner = idempotencyService.findFinalOutcome(messageId)
                .orElseThrow(() -> new IllegalStateException("Expected processed_messages row for " + messageId));
        return duplicateIgnoredOutcome(winner.getOutcome(), messageId, redelivered);
    }

    private void applySuccessOutcome(
            NotificationEntity notification,
            NotificationMessage message,
            Instant now,
            boolean redelivered,
            String consumerName
    ) {
        int attemptNumber = notification.getAttemptCount() + 1;
        recordAttempt(notification, message, attemptNumber, AttemptOutcome.SUCCESS, null, redelivered, consumerName, now);

        notification.setStatus(NotificationStatus.SENT);
        notification.setAttemptCount(attemptNumber);
        notification.setCompletedAt(now);
        notification.setUpdatedAt(now);
        notificationRepository.saveAndFlush(notification);

        idempotencyService.recordFinalOutcome(
                message.getMessageId(),
                message.getNotificationId(),
                consumerName,
                ProcessedOutcome.SENT,
                now
        );
    }

    private void applyDeadLetterOutcome(
            NotificationEntity notification,
            NotificationMessage message,
            Instant now,
            boolean redelivered,
            String consumerName,
            AttemptOutcome attemptOutcome,
            String errorMessage
    ) {
        int attemptNumber = notification.getAttemptCount() + 1;
        notification.setStatus(NotificationStatus.DEAD_LETTERED);
        notification.setAttemptCount(attemptNumber);
        notification.setLastError(errorMessage);
        notification.setCompletedAt(now);
        notification.setUpdatedAt(now);
        notificationRepository.saveAndFlush(notification);

        recordAttempt(notification, message, attemptNumber, attemptOutcome, errorMessage, redelivered, consumerName, now);

        String messageId = message.getMessageId() != null ? message.getMessageId() : notification.getMessageId();
        idempotencyService.recordFinalOutcome(
                messageId,
                notification.getId(),
                consumerName,
                ProcessedOutcome.DEAD_LETTERED,
                now
        );
    }

    private String exhaustedRetriesMessage() {
        return "retries exhausted after " + (maxRetries + 1) + " attempts";
    }

    private void deliver(NotificationMessage message) {
        if (message.getChannel() == NotificationChannel.EMAIL) {
            emailDeliverySimulator.deliver(message);
        } else if (message.getChannel() == NotificationChannel.SMS) {
            smsDeliverySimulator.deliver(message);
        } else {
            throw new PermanentDeliveryException("Unsupported channel " + message.getChannel());
        }
    }

    private NotificationEntity createNotification(NotificationMessage message, Instant now) {
        NotificationEntity entity = new NotificationEntity();
        entity.setId(message.getNotificationId());
        entity.setMessageId(message.getMessageId());
        entity.setChannel(message.getChannel());
        entity.setRecipient(message.getRecipient());
        entity.setMessage(message.getMessage());
        entity.setStatus(NotificationStatus.QUEUED);
        entity.setCorrelationId(message.getCorrelationId());
        entity.setRetryCount(message.getRetryCount());
        entity.setAttemptCount(0);
        entity.setCreatedAt(message.getCreatedAt() != null ? message.getCreatedAt() : now);
        entity.setUpdatedAt(now);
        entity.setVersion(0L);
        return entity;
    }

    private void recordAttempt(
            NotificationEntity notification,
            NotificationMessage message,
            int attemptNumber,
            AttemptOutcome outcome,
            String errorMessage,
            boolean redelivered,
            String consumerName,
            Instant now
    ) {
        NotificationAttemptEntity attempt = new NotificationAttemptEntity();
        attempt.setNotificationId(notification.getId());
        attempt.setMessageId(message.getMessageId());
        attempt.setAttemptNumber(attemptNumber);
        attempt.setConsumerName(consumerName);
        attempt.setOutcome(outcome);
        attempt.setErrorMessage(errorMessage);
        attempt.setRedelivered(redelivered);
        attempt.setStartedAt(now);
        attempt.setFinishedAt(now);
        attemptRepository.save(attempt);
    }

    private static NotificationMessage copyForRetry(NotificationMessage message) {
        return new NotificationMessage(
                message.getMessageId(),
                message.getNotificationId(),
                message.getChannel(),
                message.getRecipient(),
                message.getMessage(),
                message.getCreatedAt(),
                message.getCorrelationId(),
                message.getRetryCount() + 1
        );
    }

    private static final class DuplicateOutcomeException extends RuntimeException {
        private final ProcessedOutcome outcome;

        private DuplicateOutcomeException(ProcessedOutcome outcome) {
            super("Message already has final outcome " + outcome);
            this.outcome = outcome;
        }

        ProcessedOutcome outcome() {
            return outcome;
        }
    }
}