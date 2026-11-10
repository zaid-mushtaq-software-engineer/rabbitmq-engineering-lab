package com.example.notificationlab.producer.api;

import com.example.notificationlab.producer.api.dto.SubmitNotificationResponse;
import com.example.notificationlab.producer.config.ProducerJacksonConfig;
import com.example.notificationlab.producer.error.CorrelationIdFilter;
import com.example.notificationlab.producer.error.GlobalExceptionHandler;
import com.example.notificationlab.producer.service.NotificationQueryService;
import com.example.notificationlab.producer.service.NotificationSubmissionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = NotificationController.class)
@Import({GlobalExceptionHandler.class, CorrelationIdFilter.class, ProducerJacksonConfig.class})
@org.springframework.test.context.ActiveProfiles("test")
class NotificationControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NotificationSubmissionService submissionService;

    @MockBean
    private NotificationQueryService queryService;

    @Test
    void postReturns202WithLocation() throws Exception {
        SubmitNotificationResponse response = new SubmitNotificationResponse();
        String id = UUID.randomUUID().toString();
        response.setNotificationId(id);
        response.setMessageId(UUID.randomUUID().toString());
        response.setChannel("EMAIL");
        response.setStatus("QUEUED");
        response.setCorrelationId("corr-1");
        response.setAcceptedAt(Instant.parse("2026-11-09T10:15:30.123Z"));
        when(submissionService.submit(any(), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(CorrelationIdFilter.HEADER, "corr-1")
                        .content("""
                                {"channel":"EMAIL","recipient":"a@example.com","message":"hi"}
                                """))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", "/api/v1/notifications/" + id))
                .andExpect(jsonPath("$.status").value("QUEUED"));
    }

    @Test
    void unsupportedChannelReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"channel":"PUSH","recipient":"a@example.com","message":"hi"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_CHANNEL"));
    }

    @Test
    void unknownPropertyRejected() throws Exception {
        mockMvc.perform(post("/api/v1/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"channel":"EMAIL","recipient":"a@example.com","message":"hi","priority":"high"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }
}
