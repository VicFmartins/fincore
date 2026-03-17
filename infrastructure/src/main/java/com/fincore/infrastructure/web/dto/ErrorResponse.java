package com.fincore.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(name = "ErrorResponse", description = "Standardized error response returned by FinCore.")
public record ErrorResponse(
    @Schema(description = "UTC timestamp for the error.", example = "2026-03-17T18:45:32.114Z")
    Instant timestamp,
    @Schema(description = "HTTP status code.", example = "429")
    int status,
    @Schema(description = "Machine-readable error name.", example = "too_many_requests")
    String error,
    @Schema(description = "Human-readable error message.", example = "rate limit exceeded for write endpoints")
    String message,
    @Schema(description = "Request path that produced the error.", example = "/transactions/transfer")
    String path,
    @JsonProperty("correlation_id")
    @Schema(description = "Correlation identifier propagated across request handling.", example = "corr-7c54dff1")
    String correlationId
) {
}
