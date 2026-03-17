package com.fincore.infrastructure.web;

import com.fincore.infrastructure.observability.CorrelationContext;
import com.fincore.infrastructure.web.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.Locale;

public final class ApiErrorResponses {
    private ApiErrorResponses() {
    }

    public static ErrorResponse build(HttpStatus status, String message, HttpServletRequest request) {
        return new ErrorResponse(
            Instant.now(),
            status.value(),
            normalizeReasonPhrase(status),
            message,
            request.getRequestURI(),
            CorrelationContext.currentCorrelationId()
        );
    }

    private static String normalizeReasonPhrase(HttpStatus status) {
        return status.getReasonPhrase().toLowerCase(Locale.ROOT).replace(' ', '_');
    }
}
