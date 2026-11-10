package com.example.notificationlab.contracts;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationMessageTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Test
    void roundTripPreservesAllFields() throws Exception {
        NotificationMessage original = sample();
        String json = objectMapper.writeValueAsString(original);
        NotificationMessage restored = objectMapper.readValue(json, NotificationMessage.class);
        assertThat(restored).isEqualTo(original);
        assertThat(json).contains("2026-11-09T10:15:30.123Z");
    }

    @Test
    void unknownPropertiesAreIgnored() throws Exception {
        String json = """
                {
                  "messageId": "m1",
                  "notificationId": "n1",
                  "channel": "EMAIL",
                  "recipient": "a@example.com",
                  "message": "hi",
                  "createdAt": "2026-11-09T10:15:30.123Z",
                  "correlationId": "c1",
                  "retryCount": 0,
                  "extraField": true
                }
                """;
        NotificationMessage message = objectMapper.readValue(json, NotificationMessage.class);
        assertThat(message.getMessageId()).isEqualTo("m1");
    }

    @Test
    void missingRetryCountDefaultsToZero() throws Exception {
        String json = """
                {
                  "messageId": "m1",
                  "notificationId": "n1",
                  "channel": "EMAIL",
                  "recipient": "a@example.com",
                  "message": "hi",
                  "createdAt": "2026-11-09T10:15:30.123Z",
                  "correlationId": "c1"
                }
                """;
        NotificationMessage message = objectMapper.readValue(json, NotificationMessage.class);
        assertThat(message.getRetryCount()).isZero();
    }

    private static NotificationMessage sample() {
        return new NotificationMessage(
                "0b8f4e2a-5d3c-4c1e-9a7f-2f6d1c8e4b90",
                "7c9e6679-7425-40de-944b-e07fc1f90ae7",
                NotificationChannel.EMAIL,
                "alice@example.com",
                "Your order #1042 has shipped.",
                Instant.parse("2026-11-09T10:15:30.123Z"),
                "req-20261109-0001",
                0
        );
    }
}
