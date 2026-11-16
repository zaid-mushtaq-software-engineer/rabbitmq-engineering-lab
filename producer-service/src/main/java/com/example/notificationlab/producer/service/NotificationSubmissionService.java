package com.example.notificationlab.producer.service;

import com.example.notificationlab.contracts.NotificationChannel;
import com.example.notificationlab.producer.api.dto.SubmitNotificationRequest;
import com.example.notificationlab.producer.api.dto.SubmitNotificationResponse;
import com.example.notificationlab.producer.error.MessagePublishException;
import com.example.notificationlab.producer.error.UnsupportedChannelException;
import com.example.notificationlab.producer.messaging.NotificationMessagePublisher;
import com.example.notificationlab.producer.messaging.PublishResult;
import com.example.notificationlab.producer.persistence.ProducerNotificationEntity;
import com.example.notificationlab.producer.persistence.ProducerNotificationRepository;
import com.example.notificationlab.producer.persistence.ProducerNotificationStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;

@Service
public class NotificationSubmissionService {

    private final MessageFactory messageFactory;
    private final NotificationMessagePublisher publisher;
    private final ProducerNotificationRepository repository;
    private final TransactionTemplate transactionTemplate;

    public NotificationSubmissionService(
            MessageFactory messageFactory,
            NotificationMessagePublisher publisher,
            ProducerNotificationRepository repository,
            PlatformTransactionManager transactionManager
    ) {
        this.messageFactory = messageFactory;
        this.publisher = publisher;
        this.repository = repository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

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
        transactionTemplate.executeWithoutResult(status -> {
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
        });

        PublishResult publishResult = publisher.publishWithConfirm(built.message());

        if (!publishResult.isConfirmed()) {
            Instant failedAt = Instant.now();
            transactionTemplate.executeWithoutResult(status -> {
                repository.markPublishFailedIfPendingPublish(
                        built.notificationId(),
                        publishResult.apiFailureReason(),
                        failedAt
                );
            });
            throw new MessagePublishException(built.notificationId(), publishResult.apiFailureReason());
        }

        Instant acceptedAt = Instant.now();
        return transactionTemplate.execute(status -> {
            repository.markQueuedIfPendingPublish(built.notificationId(), acceptedAt, acceptedAt);
            ProducerNotificationEntity latest = repository.findById(built.notificationId()).orElseThrow();

            SubmitNotificationResponse response = new SubmitNotificationResponse();
            response.setNotificationId(built.notificationId());
            response.setMessageId(built.messageId());
            response.setChannel(channel.name());
            response.setStatus(mapResponseStatus(latest.getStatus()));
            response.setCorrelationId(built.correlationId());
            response.setAcceptedAt(acceptedAt);
            return response;
        });
    }

    private static String mapResponseStatus(ProducerNotificationStatus status) {
        if (status == ProducerNotificationStatus.PENDING_PUBLISH) {
            return ProducerNotificationStatus.QUEUED.name();
        }
        return status.name();
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