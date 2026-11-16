package com.example.notificationlab.consumer.messaging;

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
    private final ConcurrentMap<String, CompletableFuture<Boolean>> pending = new ConcurrentHashMap<>();

    public PublisherConfirmCoordinator(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @PostConstruct
    void registerCallbacks() {
        rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
            if (correlationData == null) {
                return;
            }
            CompletableFuture<Boolean> future = pending.get(correlationData.getId());
            if (future == null || future.isDone()) {
                return;
            }
            if (!ack) {
                future.complete(false);
            } else {
                future.complete(true);
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
        CompletableFuture<Boolean> future = pending.get(correlationId);
        if (future != null && !future.isDone()) {
            future.complete(false);
        }
    }

    public boolean awaitConfirm(String correlationId, long timeoutMillis) {
        CompletableFuture<Boolean> future = pending.get(correlationId);
        if (future == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(future.get(timeoutMillis, TimeUnit.MILLISECONDS));
        } catch (TimeoutException | InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception ex) {
            return false;
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
        CompletableFuture<Boolean> future = pending.remove(correlationId);
        if (future != null && !future.isDone()) {
            future.complete(false);
        }
    }
}