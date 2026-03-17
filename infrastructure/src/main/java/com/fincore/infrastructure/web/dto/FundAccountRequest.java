package com.fincore.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Schema(name = "FundAccountRequest", description = "Local-only account funding payload for development and demo validation.")
public record FundAccountRequest(
    @JsonProperty("amount")
    @Schema(description = "Funding amount in cents.", example = "10000", minimum = "1")
    @NotNull(message = "amount is required")
    @Positive(message = "amount must be greater than 0")
    Long amount,

    @JsonProperty("idempotency_key")
    @Schema(description = "Client supplied key to avoid duplicate local funding execution.", example = "fund-20260317-0001")
    @NotBlank(message = "idempotency_key is required")
    String idempotencyKey
) {
}
