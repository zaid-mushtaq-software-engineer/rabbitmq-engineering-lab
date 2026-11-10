package com.example.notificationlab.producer.service;

import com.example.notificationlab.contracts.NotificationChannel;
import com.example.notificationlab.contracts.RabbitNames;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RoutingKeySelectorTest {

    private final RoutingKeySelector selector = new RoutingKeySelector();

    @Test
    void mapsChannelsToRoutingKeys() {
        assertThat(selector.select(NotificationChannel.EMAIL)).isEqualTo(RabbitNames.ROUTING_EMAIL);
        assertThat(selector.select(NotificationChannel.SMS)).isEqualTo(RabbitNames.ROUTING_SMS);
    }
}
