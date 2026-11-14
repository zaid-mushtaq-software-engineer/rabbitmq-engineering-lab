package com.example.notificationlab.consumer.listener;

import com.example.notificationlab.contracts.NotificationChannel;
import com.example.notificationlab.contracts.RabbitNames;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "lab.consumer.sms.listener-enabled", havingValue = "true", matchIfMissing = true)
public class SmsNotificationListener {

    private final ChannelNotificationListenerSupport listenerSupport;

    public SmsNotificationListener(ChannelNotificationListenerSupport listenerSupport) {
        this.listenerSupport = listenerSupport;
    }

    @RabbitListener(
            id = "smsNotificationListener",
            queues = RabbitNames.SMS_QUEUE,
            containerFactory = "smsListenerContainerFactory"
    )
    public void onMessage(
            Message amqpMessage,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
            @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey,
            @Header(value = AmqpHeaders.REDELIVERED, defaultValue = "false") boolean redelivered
    ) {
        listenerSupport.handle(
                amqpMessage,
                redelivered,
                NotificationChannel.SMS,
                RabbitNames.SMS_CONSUMER,
                routingKey,
                channel,
                deliveryTag
        );
    }
}
