package com.example.notificationlab.producer.lab;

import com.example.notificationlab.contracts.NotificationChannel;
import com.example.notificationlab.producer.messaging.NotificationMessagePublisher;
import com.example.notificationlab.producer.service.MessageFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;


@Component
@Profile("lab")
public class LabNotificationPublisherRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LabNotificationPublisherRunner.class);

    private final NotificationMessagePublisher publisher;
    private final MessageFactory messageFactory;
    private final ApplicationContext applicationContext;

    public LabNotificationPublisherRunner(
            NotificationMessagePublisher publisher,
            MessageFactory messageFactory,
            ApplicationContext applicationContext
    ) {
        this.publisher = publisher;
        this.messageFactory = messageFactory;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        MessageFactory.BuiltNotification built = messageFactory.build(
                NotificationChannel.EMAIL,
                "alice@example.com",
                "Lab publish from producer-service",
                "lab-run-1"
        );
        publisher.publish(built.message());
        log.info("Published notification messageId={}", built.messageId());
        int exitCode = SpringApplication.exit(applicationContext, () -> 0);
        System.exit(exitCode);
    }
}
