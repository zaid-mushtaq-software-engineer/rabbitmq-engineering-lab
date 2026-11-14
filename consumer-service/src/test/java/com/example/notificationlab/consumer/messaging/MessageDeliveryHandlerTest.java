package com.example.notificationlab.consumer.messaging;

import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class MessageDeliveryHandlerTest {

    @Test
    void acksWhenOutcomeIsAck() throws Exception {
        MessageDeliveryHandler handler = new MessageDeliveryHandler(false);
        Channel channel = Mockito.mock(Channel.class);

        handler.apply(channel, 42L, DeliveryOutcome.ACK, "msg-1");

        verify(channel).basicAck(42L, false);
    }

    @Test
    void nacksWithoutRequeue() throws Exception {
        MessageDeliveryHandler handler = new MessageDeliveryHandler(false);
        Channel channel = Mockito.mock(Channel.class);

        handler.apply(channel, 7L, DeliveryOutcome.NACK_NO_REQUEUE, "msg-2");

        verify(channel).basicNack(7L, false, false);
    }

    @Test
    void nacksWithRequeue() throws Exception {
        MessageDeliveryHandler handler = new MessageDeliveryHandler(false);
        Channel channel = Mockito.mock(Channel.class);

        handler.apply(channel, 3L, DeliveryOutcome.NACK_REQUEUE, "msg-3");

        verify(channel).basicNack(3L, false, true);
    }

    @Test
    void defersAckWhenConfiguredForMessage() throws Exception {
        MessageDeliveryHandler handler = new MessageDeliveryHandler(true);
        handler.setDeferAckMessageId("defer-me");
        Channel channel = Mockito.mock(Channel.class);

        handler.apply(channel, 9L, DeliveryOutcome.ACK, "defer-me");

        verify(channel, never()).basicAck(9L, false);
        handler.clearDeferAck();
        handler.apply(channel, 9L, DeliveryOutcome.ACK, "defer-me");
        verify(channel).basicAck(9L, false);
    }
}
