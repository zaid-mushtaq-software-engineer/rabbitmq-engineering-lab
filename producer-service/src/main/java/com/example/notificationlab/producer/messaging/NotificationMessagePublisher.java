package com.example.notificationlab.producer.messaging;

import com.example.notificationlab.contracts.NotificationMessage;
import com.example.notificationlab.contracts.RabbitNames;
import com.example.notificationlab.producer.service.RoutingKeySelector;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class NotificationMessagePublisher {

    private final RabbitTemplate rabbitTemplate;
    private final RoutingKeySelector routingKeySelector;

    public NotificationMessagePublisher(RabbitTemplate rabbitTemplate, RoutingKeySelector routingKeySelector) {
        this.rabbitTemplate = rabbitTemplate;
        this.routingKeySelector = routingKeySelector;
    }

    public void publish(NotificationMessage notificationMessage) {
        String routingKey = routingKeySelector.select(notificationMessage.getChannel());
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
