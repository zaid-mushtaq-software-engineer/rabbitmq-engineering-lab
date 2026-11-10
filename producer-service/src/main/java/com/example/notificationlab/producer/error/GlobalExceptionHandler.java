package com.example.notificationlab.producer.error;

import com.example.notificationlab.producer.api.dto.ApiErrorResponse;
import com.example.notificationlab.producer.api.dto.ErrorDetail;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ErrorDetail> details = new ArrayList<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            ErrorDetail detail = new ErrorDetail();
            detail.setField(fieldError.getField());
            detail.setReason(fieldError.getDefaultMessage());
            details.add(detail);
        }
        return error(
                HttpStatus.BAD_REQUEST,
                ErrorCodes.VALIDATION_FAILED,
                "Request validation failed.",
                request,
                details
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        List<ErrorDetail> details = new ArrayList<>();
        if (ex.getCause() instanceof UnrecognizedPropertyException upe) {
            ErrorDetail detail = new ErrorDetail();
            detail.setField(upe.getPropertyName());
            detail.setReason("unknown property");
            details.add(detail);
            return error(HttpStatus.BAD_REQUEST, ErrorCodes.INVALID_REQUEST, "Invalid request body.", request, details);
        }
        ErrorDetail detail = new ErrorDetail();
        detail.setField(null);
        detail.setReason("Request body is missing or not valid JSON");
        details.add(detail);
        return error(HttpStatus.BAD_REQUEST, ErrorCodes.INVALID_REQUEST, "Invalid request body.", request, details);
    }

    @ExceptionHandler(UnsupportedChannelException.class)
    public ResponseEntity<ApiErrorResponse> handleUnsupportedChannel(UnsupportedChannelException ex, HttpServletRequest request) {
        ErrorDetail detail = new ErrorDetail();
        detail.setField("channel");
        detail.setReason("must be one of EMAIL, SMS");
        return error(
                HttpStatus.BAD_REQUEST,
                ErrorCodes.UNSUPPORTED_CHANNEL,
                "Channel '" + ex.getChannel() + "' is not supported.",
                request,
                List.of(detail)
        );
    }

    @ExceptionHandler(FieldValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleFieldValidation(FieldValidationException ex, HttpServletRequest request) {
        ErrorDetail detail = new ErrorDetail();
        detail.setField(ex.getField());
        detail.setReason(ex.getReason());
        return error(HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_FAILED, "Request validation failed.", request, List.of(detail));
    }

    @ExceptionHandler(InvalidPaginationException.class)
    public ResponseEntity<ApiErrorResponse> handlePagination(InvalidPaginationException ex, HttpServletRequest request) {
        ErrorDetail detail = new ErrorDetail();
        detail.setField(ex.getField());
        detail.setReason(ex.getReason());
        return error(HttpStatus.BAD_REQUEST, ErrorCodes.INVALID_PAGINATION, "Invalid pagination parameters.", request, List.of(detail));
    }

    @ExceptionHandler(InvalidFilterException.class)
    public ResponseEntity<ApiErrorResponse> handleFilter(InvalidFilterException ex, HttpServletRequest request) {
        ErrorDetail detail = new ErrorDetail();
        detail.setField(ex.getField());
        detail.setReason(ex.getReason());
        return error(HttpStatus.BAD_REQUEST, ErrorCodes.INVALID_FILTER, "Invalid filter parameters.", request, List.of(detail));
    }

    @ExceptionHandler(NotificationNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(NotificationNotFoundException ex, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, ErrorCodes.NOTIFICATION_NOT_FOUND, ex.getMessage(), request, List.of());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMediaType(HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ErrorCodes.UNSUPPORTED_MEDIA_TYPE, "Unsupported media type.", request, List.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return error(HttpStatus.METHOD_NOT_ALLOWED, ErrorCodes.METHOD_NOT_ALLOWED, "Method not allowed.", request, List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCodes.INTERNAL_ERROR, "An unexpected error occurred.", request, List.of());
    }

    private static ResponseEntity<ApiErrorResponse> error(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request,
            List<ErrorDetail> details
    ) {
        ApiErrorResponse body = new ApiErrorResponse();
        body.setTimestamp(Instant.now());
        body.setStatus(status.value());
        body.setError(status.getReasonPhrase());
        body.setCode(code);
        body.setMessage(message);
        body.setPath(request.getRequestURI());
        body.setCorrelationId(resolveCorrelationId(request));
        body.setDetails(details == null ? List.of() : details);
        return ResponseEntity.status(status).body(body);
    }

    private static String resolveCorrelationId(HttpServletRequest request) {
        Object attr = request.getAttribute(CorrelationIdFilter.REQUEST_ATTR);
        if (attr != null) {
            return attr.toString();
        }
        String mdc = MDC.get(CorrelationIdFilter.MDC_KEY);
        return mdc != null ? mdc : "req-unknown";
    }
}
