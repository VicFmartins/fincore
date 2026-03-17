package com.fincore.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fincore.infrastructure.service.FundAccountResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(name = "FundAccountResponse", description = "Local-only account funding result.")
public record FundAccountResponse(
    @JsonProperty("account_id")
    @Schema(description = "Funded account identifier.", example = "c6fb273c-44b4-4310-b13d-e6b8ec0f6ef0")
    UUID accountId,

    @JsonProperty("transaction_id")
    @Schema(description = "Generated local funding transaction identifier.", example = "2eeecae9-c925-4dad-9d89-640c0f9e70db")
    UUID transactionId,

    @Schema(description = "Funding transaction state.", example = "COMPLETED")
    String status,

    @Schema(description = "Resulting account balance in cents.", example = "10000")
    long balance,

    @JsonProperty("idempotent_replay")
    @Schema(description = "Whether the response came from an idempotent replay.", example = "false")
    boolean idempotentReplay
) {
    public static FundAccountResponse from(FundAccountResult result) {
        return new FundAccountResponse(
            result.accountId(),
            result.transactionId(),
            result.status(),
            result.balance(),
            result.idempotentReplay()
        );
    }
}
