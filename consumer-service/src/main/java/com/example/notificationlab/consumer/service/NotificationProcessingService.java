package com.example.notificationlab.consumer.service;

import com.example.notificationlab.contracts.NotificationChannel;
import com.example.notificationlab.contracts.NotificationMessage;
import com.example.notificationlab.consumer.persistence.AttemptOutcome;
import com.example.notificationlab.consumer.persistence.NotificationAttemptEntity;
import com.example.notificationlab.consumer.persistence.NotificationAttemptRepository;
import com.example.notificationlab.consumer.persistence.NotificationEntity;
import com.example.notificationlab.consumer.persistence.NotificationRepository;
import com.example.notificationlab.consumer.persistence.NotificationStatus;
import com.example.notificationlab.consumer.simulation.EmailDeliverySimulator;
import com.example.notificationlab.consumer.simulation.PermanentDeliveryException;
import com.example.notificationlab.consumer.simulation.SmsDeliverySimulator;
import com.example.notificationlab.consumer.simulation.TemporaryDeliveryException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class NotificationProcessingService {

    private static final Logger log = LoggerFactory.getLogger(NotificationProcessingService.class);

    private final NotificationRepository notificationRepository;
    private final NotificationAttemptRepository attemptRepository;
    private final EmailDeliverySimulator emailDeliverySimulator;
    private final SmsDeliverySimulator smsDeliverySimulator;

    public NotificationProcessingService(
            NotificationRepository notificationRepository,
            NotificationAttemptRepository attemptRepository,
            EmailDeliverySimulator emailDeliverySimulator,
            SmsDeliverySimulator smsDeliverySimulator
    ) {
        this.notificationRepository = notificationRepository;
        this.attemptRepository = attemptRepository;
        this.emailDeliverySimulator = emailDeliverySimulator;
        this.smsDeliverySimulator = smsDeliverySimulator;
    }

    @Transactional
    public void process(
            NotificationMessage message,
            boolean redelivered,
            NotificationChannel expectedChannel,
            String consumerName
    ) {
        if (message.getChannel() != expectedChannel) {
            log.warn("Channel mismatch for messageId={}: expected {}, got {}",
                    message.getMessageId(), expectedChannel, message.getChannel());
            return;
        }

        Instant now = Instant.now();
        NotificationEntity notification = notificationRepository
                .findByMessageId(message.getMessageId())
                .orElseGet(() -> createNotification(message, now));

        notification.setStatus(NotificationStatus.PROCESSING);
        notification.setUpdatedAt(now);
        notificationRepository.save(notification);

        try {
            deliver(message);
            recordSuccess(notification, message, now, redelivered, consumerName);
        } catch (TemporaryDeliveryException | PermanentDeliveryException ex) {
            log.warn("Delivery simulation failed for messageId={}: {}", message.getMessageId(), ex.getMessage());
        }
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

    private void recordSuccess(
            NotificationEntity notification,
            NotificationMessage message,
            Instant now,
            boolean redelivered,
            String consumerName
    ) {
        int attemptNumber = notification.getAttemptCount() + 1;
        NotificationAttemptEntity attempt = new NotificationAttemptEntity();
        attempt.setNotificationId(notification.getId());
        attempt.setMessageId(message.getMessageId());
        attempt.setAttemptNumber(attemptNumber);
        attempt.setConsumerName(consumerName);
        attempt.setOutcome(AttemptOutcome.SUCCESS);
        attempt.setRedelivered(redelivered);
        attempt.setStartedAt(now);
        attempt.setFinishedAt(now);
        attemptRepository.save(attempt);

        notification.setStatus(NotificationStatus.SENT);
        notification.setAttemptCount(attemptNumber);
        notification.setCompletedAt(now);
        notification.setUpdatedAt(now);
        notificationRepository.save(notification);
    }
}
