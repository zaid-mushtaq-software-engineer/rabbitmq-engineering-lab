package com.example.notificationlab.consumer.listener;

import com.example.notificationlab.contracts.NotificationChannel;
import com.example.notificationlab.contracts.NotificationMessage;
import com.example.notificationlab.contracts.RabbitNames;
import com.example.notificationlab.consumer.service.NotificationProcessingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "lab.consumer.sms.listener-enabled", havingValue = "true", matchIfMissing = true)
public class SmsNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(SmsNotificationListener.class);

    private final NotificationProcessingService processingService;

    public SmsNotificationListener(NotificationProcessingService processingService) {
        this.processingService = processingService;
    }

    @RabbitListener(
            queues = RabbitNames.SMS_QUEUE,
            containerFactory = "smsListenerContainerFactory"
    )
    public void onMessage(
            NotificationMessage message,
            @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey,
            @Header(value = AmqpHeaders.REDELIVERED, defaultValue = "false") boolean redelivered
    ) {
        log.info("message.received messageId={} routingKey={} redelivered={}",
                message.getMessageId(), routingKey, redelivered);
        processingService.process(message, redelivered, NotificationChannel.SMS, RabbitNames.SMS_CONSUMER);
    }
}
