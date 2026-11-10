package com.example.notificationlab.producer.api;

import com.example.notificationlab.producer.error.UnsupportedChannelException;

public final class ChannelRules {

    private ChannelRules() {
    }

    public static void assertSupported(String channel) {
        if (channel == null) {
            return;
        }
        if (!"EMAIL".equals(channel) && !"SMS".equals(channel)) {
            throw new UnsupportedChannelException(channel);
        }
    }
}
