package com.example.notificationlab.producer.error;

public class UnsupportedChannelException extends RuntimeException {

    private final String channel;

    public UnsupportedChannelException(String channel) {
        super("Channel '" + channel + "' is not supported.");
        this.channel = channel;
    }

    public String getChannel() {
        return channel;
    }
}
