package com.fincore.infrastructure.web;

import com.fincore.infrastructure.service.FundAccountResult;
import com.fincore.infrastructure.service.LocalFundingService;
import com.fincore.infrastructure.web.dto.ErrorResponse;
import com.fincore.infrastructure.web.dto.FundAccountRequest;
import com.fincore.infrastructure.web.dto.FundAccountResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@Profile("local")
@RequestMapping("/accounts")
@Tag(name = "Accounts", description = "Account creation and read operations.")
public class LocalFundingController {
    private final LocalFundingService localFundingService;

    public LocalFundingController(LocalFundingService localFundingService) {
        this.localFundingService = localFundingService;
    }

    @PostMapping("/{id}/fund")
    @Operation(
        summary = "Fund account locally",
        description = "Local-profile-only convenience endpoint for demos and black-box validation. Not intended for production deployments.",
        security = @SecurityRequirement(name = "apiKeyAuth")
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Funding completed",
            content = @Content(
                schema = @Schema(implementation = FundAccountResponse.class),
                examples = @ExampleObject(value = "{\"account_id\":\"c6fb273c-44b4-4310-b13d-e6b8ec0f6ef0\",\"transaction_id\":\"2eeecae9-c925-4dad-9d89-640c0f9e70db\",\"status\":\"COMPLETED\",\"balance\":10000,\"idempotent_replay\":false}")
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Validation or malformed request error",
            content = @Content(
                schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"timestamp\":\"2026-03-17T18:45:32.114Z\",\"status\":400,\"error\":\"bad_request\",\"message\":\"amount must be greater than 0\",\"path\":\"/accounts/c6fb273c-44b4-4310-b13d-e6b8ec0f6ef0/fund\",\"correlation_id\":\"corr-123\"}")
            )
        ),
        @ApiResponse(
            responseCode = "401",
            description = "Authentication required or invalid API key",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        ),
        @ApiResponse(
            responseCode = "403",
            description = "API key lacks write permission",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Account not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        ),
        @ApiResponse(
            responseCode = "409",
            description = "Idempotency key reused with a conflicting payload",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        ),
        @ApiResponse(
            responseCode = "429",
            description = "Write rate limit exceeded",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        )
    })
    public ResponseEntity<FundAccountResponse> fundAccount(@PathVariable UUID id,
                                                           @Valid @RequestBody FundAccountRequest request) {
        FundAccountResult result = localFundingService.fund(id, request.amount(), request.idempotencyKey());
        return ResponseEntity.ok(FundAccountResponse.from(result));
    }
}
