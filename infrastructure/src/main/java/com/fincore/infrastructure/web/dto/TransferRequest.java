package com.fincore.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fincore.application.usecases.TransferCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

@Schema(name = "TransferRequest", description = "Transfer instruction payload.")
public record TransferRequest(
    @JsonProperty("source_account_id")
    @Schema(description = "Debited account identifier.", example = "7d207d18-b633-49d1-8f1f-cdf9634a2175")
    @NotNull(message = "source_account_id is required")
    UUID sourceAccountId,

    @JsonProperty("destination_account_id")
    @Schema(description = "Credited account identifier.", example = "5f95572d-1168-458f-ad16-a7c3906ec103")
    @NotNull(message = "destination_account_id is required")
    UUID destinationAccountId,

    @JsonProperty("amount")
    @Schema(description = "Transfer amount in cents.", example = "2500", minimum = "1")
    @NotNull(message = "amount is required")
    @Positive(message = "amount must be greater than 0")
    Long amount,

    @JsonProperty("idempotency_key")
    @Schema(description = "Client supplied key to prevent duplicate transfer execution.", example = "transfer-20260317-0001")
    @NotBlank(message = "idempotency_key is required")
    String idempotencyKey
) {
    public TransferCommand toCommand() {
        return new TransferCommand(sourceAccountId, destinationAccountId, amount, idempotencyKey);
    }
}
