package com.example.notificationlab.producer.error;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";
    public static final String REQUEST_ATTR = "correlationId";

    private static final Pattern CORRELATION_PATTERN = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String header = request.getHeader(HEADER);
        String correlationId;
        if (header == null || header.isBlank()) {
            correlationId = "req-" + UUID.randomUUID();
        } else if (!CORRELATION_PATTERN.matcher(header).matches()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setContentType("application/json");
            response.getWriter().write("""
                    {"timestamp":"%s","status":400,"error":"Bad Request","code":"VALIDATION_FAILED",
                    "message":"Request validation failed.","path":"%s","correlationId":"%s",
                    "details":[{"field":"X-Correlation-Id","reason":"must match [A-Za-z0-9._-]{1,64}"}]}
                    """.formatted(java.time.Instant.now(), request.getRequestURI(), header));
            return;
        } else {
            correlationId = header;
        }
        MDC.put(MDC_KEY, correlationId);
        request.setAttribute(REQUEST_ATTR, correlationId);
        response.setHeader(HEADER, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
