package com.example.notificationlab.consumer.lab;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("lab-exchanges")
@ConditionalOnProperty(name = "lab.consumer.lab-exchange.listener-enabled", havingValue = "true")
public class LabExchangeLoggingListener {

    private static final Logger log = LoggerFactory.getLogger(LabExchangeLoggingListener.class);

    @RabbitListener(queues = {
            "lab.topic.all-channels.queue",
            "lab.topic.email-any.queue",
            "lab.topic.high-priority.queue",
            "lab.fanout.audit.queue",
            "lab.fanout.analytics.queue"
    })
    public void onLabMessage(String body) {
        log.info("Lab queue received payload length={}", body == null ? 0 : body.length());
    }
}
