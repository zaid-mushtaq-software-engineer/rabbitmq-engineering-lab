package com.example.notificationlab.producer.service;

import com.example.notificationlab.contracts.NotificationChannel;
import com.example.notificationlab.contracts.RabbitNames;
import org.springframework.stereotype.Component;

@Component
public class RoutingKeySelector {

    public String select(NotificationChannel channel) {
        return switch (channel) {
            case EMAIL -> RabbitNames.ROUTING_EMAIL;
            case SMS -> RabbitNames.ROUTING_SMS;
        };
    }
}
