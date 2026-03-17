package com.fincore.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fincore.application.usecases.TransferResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(name = "TransferResponse", description = "Transfer execution result.")
public record TransferResponse(
    @JsonProperty("transaction_id")
    @Schema(description = "Created transaction identifier.", example = "2eeecae9-c925-4dad-9d89-640c0f9e70db")
    UUID transactionId,
    @Schema(description = "Transaction state.", example = "COMPLETED")
    String status
) {
    public static TransferResponse from(TransferResult result) {
        return new TransferResponse(result.getTransactionId(), result.getStatus().name());
    }
}
