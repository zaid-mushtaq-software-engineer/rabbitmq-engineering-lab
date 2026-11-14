package com.example.notificationlab.consumer.messaging;

import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class MessageDeliveryHandler {

    private static final Logger log = LoggerFactory.getLogger(MessageDeliveryHandler.class);

    private final boolean deferAckForTests;
    private final AtomicReference<String> deferMessageId = new AtomicReference<>();

    public MessageDeliveryHandler(
            @Value("${lab.consumer.ack.defer-for-tests:false}") boolean deferAckForTests
    ) {
        this.deferAckForTests = deferAckForTests;
    }

    public void apply(Channel channel, long deliveryTag, DeliveryOutcome outcome, String messageId) throws IOException {
        if (deferAckForTests && deferMessageId.get() != null && deferMessageId.get().equals(messageId)) {
            log.info("ack.deferred deliveryTag={} messageId={}", deliveryTag, messageId);
            return;
        }
        switch (outcome) {
            case ACK -> {
                channel.basicAck(deliveryTag, false);
                log.debug("ack.sent deliveryTag={} messageId={}", deliveryTag, messageId);
            }
            case NACK_NO_REQUEUE -> {
                channel.basicNack(deliveryTag, false, false);
                log.info("nack.sent requeue=false deliveryTag={} messageId={}", deliveryTag, messageId);
            }
            case NACK_REQUEUE -> {
                channel.basicNack(deliveryTag, false, true);
                log.info("nack.sent requeue=true deliveryTag={} messageId={}", deliveryTag, messageId);
            }
        }
    }

    public void ack(Channel channel, long deliveryTag, String messageId) throws IOException {
        apply(channel, deliveryTag, DeliveryOutcome.ACK, messageId);
    }

    public void setDeferAckMessageId(String messageId) {
        deferMessageId.set(messageId);
    }

    public void clearDeferAck() {
        deferMessageId.set(null);
    }
}
