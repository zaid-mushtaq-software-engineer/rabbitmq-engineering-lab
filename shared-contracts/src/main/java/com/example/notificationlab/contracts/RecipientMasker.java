package com.example.notificationlab.contracts;

import java.util.regex.Pattern;

/**
 * Masks notification recipients for safe logging (implementation.md §14).
 */
public final class RecipientMasker {

    private static final Pattern EMAIL = Pattern.compile("^([^@]+)@(.+)$");

    private RecipientMasker() {
    }

  public static String mask(String recipient) {
        if (recipient == null || recipient.isBlank()) {
            return "***";
        }
        String trimmed = recipient.trim();
        if (trimmed.startsWith("+") || trimmed.matches("^\\d{10,15}$")) {
            return maskPhone(trimmed);
        }
        var matcher = EMAIL.matcher(trimmed);
        if (matcher.matches()) {
            return maskEmailLocal(matcher.group(1)) + "@" + matcher.group(2);
        }
        return maskGeneric(trimmed);
    }

    private static String maskEmailLocal(String local) {
        if (local.isEmpty()) {
            return "***";
        }
        if (local.length() == 1) {
            return local.charAt(0) + "***";
        }
        return local.charAt(0) + "***";
    }

    private static String maskPhone(String phone) {
        String digits = phone.startsWith("+") ? phone : phone;
        if (digits.length() <= 4) {
            return "***";
        }
        int keepStart = Math.min(3, digits.length() / 3);
        int keepEnd = Math.min(4, digits.length() - keepStart);
        String start = digits.substring(0, keepStart);
        String end = digits.substring(digits.length() - keepEnd);
        return start + "***" + end;
    }

    private static String maskGeneric(String value) {
        if (value.length() <= 2) {
            return "***";
        }
        return value.charAt(0) + "***" + value.charAt(value.length() - 1);
    }
}