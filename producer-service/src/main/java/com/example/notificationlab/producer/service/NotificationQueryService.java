package com.example.notificationlab.producer.service;

import com.example.notificationlab.contracts.NotificationChannel;
import com.example.notificationlab.producer.api.dto.AttemptResponse;
import com.example.notificationlab.producer.api.dto.NotificationDetailResponse;
import com.example.notificationlab.producer.api.dto.NotificationPageResponse;
import com.example.notificationlab.producer.api.dto.NotificationSummaryResponse;
import com.example.notificationlab.producer.error.FieldValidationException;
import com.example.notificationlab.producer.error.InvalidFilterException;
import com.example.notificationlab.producer.error.InvalidPaginationException;
import com.example.notificationlab.producer.error.NotificationNotFoundException;
import com.example.notificationlab.producer.persistence.ProducerAttemptEntity;
import com.example.notificationlab.producer.persistence.ProducerAttemptRepository;
import com.example.notificationlab.producer.persistence.ProducerNotificationEntity;
import com.example.notificationlab.producer.persistence.ProducerNotificationRepository;
import com.example.notificationlab.producer.persistence.ProducerNotificationStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class NotificationQueryService {

    private final ProducerNotificationRepository notificationRepository;
    private final ProducerAttemptRepository attemptRepository;
    private final StatusDescriptionMapper statusDescriptionMapper;
    private final int defaultPageSize;
    private final int maxPageSize;

    public NotificationQueryService(
            ProducerNotificationRepository notificationRepository,
            ProducerAttemptRepository attemptRepository,
            StatusDescriptionMapper statusDescriptionMapper,
            @Value("${lab.api.default-page-size:20}") int defaultPageSize,
            @Value("${lab.api.max-page-size:100}") int maxPageSize
    ) {
        this.notificationRepository = notificationRepository;
        this.attemptRepository = attemptRepository;
        this.statusDescriptionMapper = statusDescriptionMapper;
        this.defaultPageSize = defaultPageSize;
        this.maxPageSize = maxPageSize;
    }

    public NotificationDetailResponse getById(String id) {
        validateUuid(id);
        ProducerNotificationEntity entity = notificationRepository.findById(id)
                .orElseThrow(() -> new NotificationNotFoundException(id));
        List<ProducerAttemptEntity> attempts = attemptRepository.findByNotificationIdOrderByAttemptNumberAsc(id);

        NotificationDetailResponse response = new NotificationDetailResponse();
        response.setNotificationId(entity.getId());
        response.setMessageId(entity.getMessageId());
        response.setChannel(entity.getChannel().name());
        response.setRecipient(entity.getRecipient());
        response.setMessage(entity.getMessage());
        response.setStatus(entity.getStatus().name());
        response.setStatusDescription(statusDescriptionMapper.describe(entity.getStatus()));
        response.setRetryCount(entity.getRetryCount());
        response.setAttemptCount(entity.getAttemptCount());
        response.setLastError(entity.getLastError());
        response.setCorrelationId(entity.getCorrelationId());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        response.setQueuedAt(entity.getQueuedAt());
        response.setCompletedAt(entity.getCompletedAt());
        response.setAttempts(attempts.stream().map(this::toAttempt).toList());
        return response;
    }

    public NotificationPageResponse list(
            String status,
            String channel,
            Instant createdFrom,
            Instant createdTo,
            Integer page,
            Integer size,
            String sort
    ) {
        int effectivePage = page == null ? 0 : page;
        if (effectivePage < 0) {
            throw new InvalidPaginationException("page", "must be an integer >= 0");
        }
        int effectiveSize = size == null ? defaultPageSize : size;
        if (effectiveSize < 1 || effectiveSize > maxPageSize) {
            throw new InvalidPaginationException("size", "must be between 1 and " + maxPageSize);
        }
        if (createdFrom != null && createdTo != null && !createdTo.isAfter(createdFrom)) {
            throw new InvalidFilterException("createdTo", "must be after createdFrom");
        }

        Sort sortOrder = parseSort(sort);
        Specification<ProducerNotificationEntity> spec = buildSpec(status, channel, createdFrom, createdTo);
        Page<ProducerNotificationEntity> result = notificationRepository.findAll(
                spec,
                PageRequest.of(effectivePage, effectiveSize, sortOrder)
        );

        NotificationPageResponse pageResponse = new NotificationPageResponse();
        pageResponse.setPage(result.getNumber());
        pageResponse.setSize(result.getSize());
        pageResponse.setTotalElements(result.getTotalElements());
        pageResponse.setTotalPages(result.getTotalPages());
        pageResponse.setFirst(result.isFirst());
        pageResponse.setLast(result.isLast());
        pageResponse.setContent(result.getContent().stream().map(this::toSummary).toList());
        return pageResponse;
    }

    private Sort parseSort(String sort) {
        String raw = sort == null || sort.isBlank() ? "createdAt,desc" : sort;
        String[] parts = raw.split(",");
        if (parts.length != 2) {
            throw new InvalidPaginationException("sort", "allowed: createdAt, updatedAt with asc or desc");
        }
        String field = parts[0].trim();
        String direction = parts[1].trim();
        if (!List.of("createdAt", "updatedAt").contains(field)
                || !List.of("asc", "desc").contains(direction)) {
            throw new InvalidPaginationException("sort", "allowed: createdAt, updatedAt with asc or desc");
        }
        Sort primary = Sort.by(Sort.Direction.fromString(direction), mapSortField(field));
        return primary.and(Sort.by("id").ascending());
    }

    private static String mapSortField(String field) {
        return switch (field) {
            case "createdAt" -> "createdAt";
            case "updatedAt" -> "updatedAt";
            default -> throw new InvalidPaginationException("sort", "allowed: createdAt, updatedAt with asc or desc");
        };
    }

    private Specification<ProducerNotificationEntity> buildSpec(
            String status,
            String channel,
            Instant createdFrom,
            Instant createdTo
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null && !status.isBlank()) {
                try {
                    ProducerNotificationStatus s = ProducerNotificationStatus.valueOf(status);
                    predicates.add(cb.equal(root.get("status"), s));
                } catch (IllegalArgumentException ex) {
                    throw new InvalidFilterException("status", "must be one of PENDING_PUBLISH, QUEUED, ...");
                }
            }
            if (channel != null && !channel.isBlank()) {
                try {
                    NotificationChannel c = NotificationChannel.valueOf(channel);
                    predicates.add(cb.equal(root.get("channel"), c));
                } catch (IllegalArgumentException ex) {
                    throw new InvalidFilterException("channel", "must be one of EMAIL, SMS");
                }
            }
            if (createdFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), createdFrom));
            }
            if (createdTo != null) {
                predicates.add(cb.lessThan(root.get("createdAt"), createdTo));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private AttemptResponse toAttempt(ProducerAttemptEntity entity) {
        AttemptResponse response = new AttemptResponse();
        response.setAttemptNumber(entity.getAttemptNumber());
        response.setConsumerName(entity.getConsumerName());
        response.setOutcome(entity.getOutcome());
        response.setErrorCode(entity.getErrorCode());
        response.setErrorMessage(entity.getErrorMessage());
        response.setRedelivered(entity.isRedelivered());
        response.setStartedAt(entity.getStartedAt());
        response.setFinishedAt(entity.getFinishedAt());
        return response;
    }

    private NotificationSummaryResponse toSummary(ProducerNotificationEntity entity) {
        NotificationSummaryResponse response = new NotificationSummaryResponse();
        response.setNotificationId(entity.getId());
        response.setChannel(entity.getChannel().name());
        response.setRecipient(entity.getRecipient());
        response.setStatus(entity.getStatus().name());
        response.setRetryCount(entity.getRetryCount());
        response.setAttemptCount(entity.getAttemptCount());
        response.setLastError(entity.getLastError());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }

    private static void validateUuid(String id) {
        try {
            UUID.fromString(id);
        } catch (IllegalArgumentException ex) {
            throw new FieldValidationException("id", "must be a valid UUID");
        }
    }
}
