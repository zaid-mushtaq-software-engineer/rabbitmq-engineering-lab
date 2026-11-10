package com.example.notificationlab.producer.api.validation;

import com.example.notificationlab.contracts.NotificationChannel;
import com.example.notificationlab.producer.api.dto.SubmitNotificationRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class NotificationRequestValidator implements ConstraintValidator<ValidNotificationRequest, SubmitNotificationRequest> {

    @Override
    public boolean isValid(SubmitNotificationRequest request, ConstraintValidatorContext context) {
        if (request == null) {
            return true;
        }
        if (request.getMessage() != null) {
            String trimmed = request.getMessage().trim();
            if (trimmed.length() < 1 || trimmed.length() > 1000) {
                addViolation(context, "message", "length must be between 1 and 1000");
                return false;
            }
        }
        String rawChannel = request.getChannel();
        if (!"EMAIL".equals(rawChannel) && !"SMS".equals(rawChannel)) {
            return true;
        }
        NotificationChannel channel = NotificationChannel.valueOf(rawChannel);
        String recipientError = RecipientRules.validate(channel, request.getRecipient());
        if (recipientError != null) {
            addViolation(context, "recipient", recipientError);
            return false;
        }
        return true;
    }

    private static void addViolation(ConstraintValidatorContext context, String field, String reason) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(reason)
                .addPropertyNode(field)
                .addConstraintViolation();
    }
}
