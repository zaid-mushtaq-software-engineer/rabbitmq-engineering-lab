package com.example.notificationlab.consumer.listener;

import com.example.notificationlab.contracts.NotificationChannel;
import com.example.notificationlab.contracts.NotificationMessage;
import com.example.notificationlab.contracts.RabbitNames;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
public class EmailNotificationListener {

    private final ChannelNotificationListenerSupport listenerSupport;

    public EmailNotificationListener(ChannelNotificationListenerSupport listenerSupport) {
        this.listenerSupport = listenerSupport;
    }

    @RabbitListener(
            id = "emailNotificationListener",
            queues = RabbitNames.EMAIL_QUEUE,
            containerFactory = "emailListenerContainerFactory"
    )
    public void onMessage(
            NotificationMessage message,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
            @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey,
            @Header(value = AmqpHeaders.REDELIVERED, defaultValue = "false") boolean redelivered
    ) {
        listenerSupport.handle(
                message,
                redelivered,
                NotificationChannel.EMAIL,
                RabbitNames.EMAIL_CONSUMER,
                routingKey,
                channel,
                deliveryTag
        );
    }
}
