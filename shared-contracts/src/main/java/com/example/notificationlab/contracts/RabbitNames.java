package com.example.notificationlab.contracts;

public final class RabbitNames {

    public static final String DIRECT_EXCHANGE = "notification.direct.exchange";
    public static final String RETRY_EXCHANGE = "notification.retry.exchange";
    public static final String DLX_EXCHANGE = "notification.dlx.exchange";
    public static final String TOPIC_EXCHANGE = "notification.topic.exchange";
    public static final String FANOUT_EXCHANGE = "notification.fanout.exchange";

    public static final String EMAIL_QUEUE = "email.queue";
    public static final String SMS_QUEUE = "sms.queue";
    public static final String EMAIL_RETRY_QUEUE = "email.retry.queue";
    public static final String SMS_RETRY_QUEUE = "sms.retry.queue";
    public static final String DEAD_LETTER_QUEUE = "notification.dlq";

    public static final String ROUTING_EMAIL = "notification.email";
    public static final String ROUTING_SMS = "notification.sms";
    public static final String ROUTING_EMAIL_RETRY = "email.retry";
    public static final String ROUTING_SMS_RETRY = "sms.retry";
    public static final String ROUTING_DEAD = "notification.dead";

    public static final String EMAIL_CONSUMER = "email-consumer";
    public static final String SMS_CONSUMER = "sms-consumer";

    private RabbitNames() {
    }
}
