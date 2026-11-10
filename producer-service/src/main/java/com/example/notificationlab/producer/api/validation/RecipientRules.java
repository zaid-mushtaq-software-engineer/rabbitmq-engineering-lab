package com.example.notificationlab.producer.api.validation;

import com.example.notificationlab.contracts.NotificationChannel;

import java.util.regex.Pattern;

public final class RecipientRules {

    private static final Pattern EMAIL = Pattern.compile(
            "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    );
    private static final Pattern E164 = Pattern.compile("^\\+[1-9]\\d{6,14}$");

    private RecipientRules() {
    }

    public static String validate(NotificationChannel channel, String recipient) {
        if (recipient == null || recipient.isBlank()) {
            return "must not be blank";
        }
        return switch (channel) {
            case EMAIL -> {
                if (recipient.length() > 254) {
                    yield "must be a valid email address";
                }
                yield EMAIL.matcher(recipient).matches() ? null : "must be a valid email address";
            }
            case SMS -> E164.matcher(recipient).matches()
                    ? null
                    : "must be in E.164 format, e.g. +15550100099";
        };
    }
}
