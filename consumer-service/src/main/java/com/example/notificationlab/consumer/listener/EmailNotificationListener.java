package com.example.notificationlab.consumer.listener;

import com.example.notificationlab.contracts.NotificationChannel;
import com.example.notificationlab.contracts.NotificationMessage;
import com.example.notificationlab.contracts.RabbitNames;
import com.example.notificationlab.consumer.service.NotificationProcessingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
public class EmailNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationListener.class);

    private final NotificationProcessingService processingService;

    public EmailNotificationListener(NotificationProcessingService processingService) {
        this.processingService = processingService;
    }

    @RabbitListener(
            queues = RabbitNames.EMAIL_QUEUE,
            containerFactory = "emailListenerContainerFactory"
    )
    public void onMessage(
            NotificationMessage message,
            @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey,
            @Header(value = AmqpHeaders.REDELIVERED, defaultValue = "false") boolean redelivered
    ) {
        log.info("message.received messageId={} routingKey={} redelivered={}",
                message.getMessageId(), routingKey, redelivered);
        processingService.process(message, redelivered, NotificationChannel.EMAIL, RabbitNames.EMAIL_CONSUMER);
    }
}
