package com.example.notificationlab.producer.service;

import com.example.notificationlab.contracts.NotificationChannel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MessageFactoryTest {

    private final MessageFactory factory = new MessageFactory();

    @Test
    void buildsMessageWithRetryCountZeroAndIds() {
        MessageFactory.BuiltNotification built = factory.build(
                NotificationChannel.EMAIL,
                "alice@example.com",
                "hello",
                "corr-1"
        );
        assertThat(built.notificationId()).isNotBlank();
        assertThat(built.messageId()).isNotBlank();
        assertThat(built.correlationId()).isEqualTo("corr-1");
        assertThat(built.message().getRetryCount()).isZero();
        assertThat(built.message().getCreatedAt()).isNotNull();
    }

    @Test
    void generatesCorrelationIdWhenAbsent() {
        MessageFactory.BuiltNotification built = factory.build(
                NotificationChannel.SMS,
                "+15550100099",
                "hi",
                null
        );
        assertThat(built.correlationId()).startsWith("req-");
    }
}
