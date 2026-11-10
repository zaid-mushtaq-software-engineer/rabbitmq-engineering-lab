package com.example.notificationlab.producer.lab;

import com.example.notificationlab.contracts.NotificationChannel;
import com.example.notificationlab.contracts.NotificationMessage;
import com.example.notificationlab.producer.messaging.NotificationMessagePublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
@Profile("lab")
public class LabNotificationPublisherRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LabNotificationPublisherRunner.class);

    private final NotificationMessagePublisher publisher;
    private final ApplicationContext applicationContext;

    public LabNotificationPublisherRunner(
            NotificationMessagePublisher publisher,
            ApplicationContext applicationContext
    ) {
        this.publisher = publisher;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        NotificationMessage message = new NotificationMessage(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                NotificationChannel.EMAIL,
                "alice@example.com",
                "Lab publish from producer-service",
                Instant.now(),
                "lab-run-1",
                0
        );
        publisher.publish(message);
        log.info("Published notification messageId={}", message.getMessageId());
        int exitCode = SpringApplication.exit(applicationContext, () -> 0);
        System.exit(exitCode);
    }
}
