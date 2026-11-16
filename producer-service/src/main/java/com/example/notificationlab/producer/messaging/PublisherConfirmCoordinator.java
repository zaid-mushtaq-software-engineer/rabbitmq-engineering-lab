package com.example.notificationlab.producer.messaging;

import jakarta.annotation.PostConstruct;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class PublisherConfirmCoordinator {

    private final RabbitTemplate rabbitTemplate;
    private final ConcurrentMap<String, CompletableFuture<PublishOutcome>> pending = new ConcurrentHashMap<>();

    public PublisherConfirmCoordinator(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @PostConstruct
    void registerCallbacks() {
        rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
            if (correlationData == null) {
                return;
            }
            CompletableFuture<PublishOutcome> future = pending.get(correlationData.getId());
            if (future == null || future.isDone()) {
                return;
            }
            if (!ack) {
                future.complete(PublishOutcome.NACKED);
            } else {
                future.complete(PublishOutcome.CONFIRMED);
            }
        });
        rabbitTemplate.setReturnsCallback(this::handleReturn);
    }

    private void handleReturn(ReturnedMessage returned) {
        if (returned == null || returned.getMessage() == null) {
            return;
        }
        String correlationId = returned.getMessage().getMessageProperties().getCorrelationId();
        if (correlationId == null) {
            return;
        }
        CompletableFuture<PublishOutcome> future = pending.get(correlationId);
        if (future != null && !future.isDone()) {
            future.complete(PublishOutcome.RETURNED_UNROUTABLE);
        }
    }

    public PublishResult awaitConfirm(String correlationId, long timeoutMillis) {
        CompletableFuture<PublishOutcome> future = pending.get(correlationId);
        if (future == null) {
            return PublishResult.of(PublishOutcome.BROKER_UNAVAILABLE, "Publish was not registered for confirm tracking");
        }
        try {
            PublishOutcome outcome = future.get(timeoutMillis, TimeUnit.MILLISECONDS);
            return new PublishResult(outcome, null);
        } catch (TimeoutException ex) {
            return PublishResult.of(PublishOutcome.TIMED_OUT, "Broker did not confirm the message within the configured timeout");
        } catch (Exception ex) {
            return PublishResult.of(PublishOutcome.BROKER_UNAVAILABLE, ex.getMessage());
        } finally {
            pending.remove(correlationId);
        }
    }

    public String beginPublish() {
        String correlationId = java.util.UUID.randomUUID().toString();
        pending.put(correlationId, new CompletableFuture<>());
        return correlationId;
    }

    public void publishFailedBeforeSend(String correlationId) {
        CompletableFuture<PublishOutcome> future = pending.remove(correlationId);
        if (future != null && !future.isDone()) {
            future.complete(PublishOutcome.BROKER_UNAVAILABLE);
        }
    }
}