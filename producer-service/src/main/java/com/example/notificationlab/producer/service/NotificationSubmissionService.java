package com.example.notificationlab.producer.service;

import com.example.notificationlab.contracts.NotificationChannel;
import com.example.notificationlab.producer.api.dto.SubmitNotificationRequest;
import com.example.notificationlab.producer.api.dto.SubmitNotificationResponse;
import com.example.notificationlab.producer.error.UnsupportedChannelException;
import com.example.notificationlab.producer.messaging.NotificationMessagePublisher;
import com.example.notificationlab.producer.persistence.ProducerNotificationEntity;
import com.example.notificationlab.producer.persistence.ProducerNotificationRepository;
import com.example.notificationlab.producer.persistence.ProducerNotificationStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Phase 3: publishes without waiting for broker confirms (Phase 6 adds confirm handling).
 */
@Service
public class NotificationSubmissionService {

    private final MessageFactory messageFactory;
    private final NotificationMessagePublisher publisher;
    private final ProducerNotificationRepository repository;

    public NotificationSubmissionService(
            MessageFactory messageFactory,
            NotificationMessagePublisher publisher,
            ProducerNotificationRepository repository
    ) {
        this.messageFactory = messageFactory;
        this.publisher = publisher;
        this.repository = repository;
    }

    @Transactional
    public SubmitNotificationResponse submit(SubmitNotificationRequest request, String correlationId) {
        NotificationChannel channel = parseChannel(request.getChannel());
        String messageText = request.getMessage().trim();
        MessageFactory.BuiltNotification built = messageFactory.build(
                channel,
                request.getRecipient(),
                messageText,
                correlationId
        );

        Instant now = Instant.now();
        ProducerNotificationEntity record = new ProducerNotificationEntity();
        record.setId(built.notificationId());
        record.setMessageId(built.messageId());
        record.setChannel(channel);
        record.setRecipient(request.getRecipient());
        record.setMessage(messageText);
        record.setStatus(ProducerNotificationStatus.PENDING_PUBLISH);
        record.setCorrelationId(built.correlationId());
        record.setRetryCount(0);
        record.setAttemptCount(0);
        record.setCreatedAt(built.message().getCreatedAt());
        record.setUpdatedAt(now);
        record.setVersion(0L);
        repository.save(record);

        publisher.publish(built.message());

        Instant acceptedAt = Instant.now();
        repository.markQueuedIfPendingPublish(built.notificationId(), acceptedAt, acceptedAt);

        SubmitNotificationResponse response = new SubmitNotificationResponse();
        response.setNotificationId(built.notificationId());
        response.setMessageId(built.messageId());
        response.setChannel(channel.name());
        response.setStatus("QUEUED");
        response.setCorrelationId(built.correlationId());
        response.setAcceptedAt(acceptedAt);
        return response;
    }

    private static NotificationChannel parseChannel(String raw) {
        if (raw == null) {
            throw new UnsupportedChannelException("null");
        }
        try {
            return NotificationChannel.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            throw new UnsupportedChannelException(raw);
        }
    }
}
