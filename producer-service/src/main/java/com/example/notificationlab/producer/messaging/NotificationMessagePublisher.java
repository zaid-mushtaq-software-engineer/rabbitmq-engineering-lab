package com.example.notificationlab.producer.messaging;

import com.example.notificationlab.contracts.NotificationChannel;
import com.example.notificationlab.contracts.NotificationMessage;
import com.example.notificationlab.contracts.RabbitNames;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class NotificationMessagePublisher {

    private final RabbitTemplate rabbitTemplate;

    public NotificationMessagePublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publish(NotificationMessage notificationMessage) {
        String routingKey = notificationMessage.getChannel() == NotificationChannel.EMAIL
                ? RabbitNames.ROUTING_EMAIL
                : RabbitNames.ROUTING_SMS;
        rabbitTemplate.convertAndSend(RabbitNames.DIRECT_EXCHANGE, routingKey, notificationMessage, message -> {
            MessageProperties properties = message.getMessageProperties();
            properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
            properties.setMessageId(notificationMessage.getMessageId());
            properties.setCorrelationId(notificationMessage.getCorrelationId());
            if (notificationMessage.getCreatedAt() != null) {
                properties.setTimestamp(java.util.Date.from(notificationMessage.getCreatedAt()));
            }
            return message;
        });
    }
}
