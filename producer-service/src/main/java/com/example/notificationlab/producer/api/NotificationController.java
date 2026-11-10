package com.example.notificationlab.producer.api;

import com.example.notificationlab.producer.api.dto.NotificationDetailResponse;
import com.example.notificationlab.producer.api.dto.NotificationPageResponse;
import com.example.notificationlab.producer.api.dto.SubmitNotificationRequest;
import com.example.notificationlab.producer.api.dto.SubmitNotificationResponse;
import com.example.notificationlab.producer.error.CorrelationIdFilter;
import com.example.notificationlab.producer.service.NotificationQueryService;
import com.example.notificationlab.producer.service.NotificationSubmissionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationSubmissionService submissionService;
    private final NotificationQueryService queryService;

    public NotificationController(
            NotificationSubmissionService submissionService,
            NotificationQueryService queryService
    ) {
        this.submissionService = submissionService;
        this.queryService = queryService;
    }

    @PostMapping
    public ResponseEntity<SubmitNotificationResponse> submit(
            @Valid @RequestBody SubmitNotificationRequest request,
            HttpServletRequest httpServletRequest
    ) {
        ChannelRules.assertSupported(request.getChannel());
        String correlationId = (String) httpServletRequest.getAttribute(CorrelationIdFilter.REQUEST_ATTR);
        SubmitNotificationResponse body = submissionService.submit(request, correlationId);
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.LOCATION, "/api/v1/notifications/" + body.getNotificationId());
        headers.set(CorrelationIdFilter.HEADER, body.getCorrelationId());
        return new ResponseEntity<>(body, headers, HttpStatus.ACCEPTED);
    }

    @GetMapping("/{id}")
    public NotificationDetailResponse getById(@PathVariable("id") String id, HttpServletRequest request) {
        return queryService.getById(id);
    }

    @GetMapping
    public NotificationPageResponse list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) Instant createdFrom,
            @RequestParam(required = false) Instant createdTo,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort
    ) {
        return queryService.list(status, channel, createdFrom, createdTo, page, size, sort);
    }
}
