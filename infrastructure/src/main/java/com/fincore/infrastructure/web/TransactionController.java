package com.fincore.infrastructure.web;

import com.fincore.application.usecases.TransferResult;
import com.fincore.infrastructure.service.TransferService;
import com.fincore.infrastructure.web.dto.ErrorResponse;
import com.fincore.infrastructure.web.dto.TransferRequest;
import com.fincore.infrastructure.web.dto.TransferResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/transactions")
@Tag(name = "Transactions", description = "Money movement endpoints.")
public class TransactionController {
    private final TransferService transferService;

    public TransactionController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping("/transfer")
    @Operation(
        summary = "Transfer funds",
        description = "Moves funds atomically between two accounts with idempotency protection.",
        security = @SecurityRequirement(name = "apiKeyAuth")
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Transfer completed",
            content = @Content(
                schema = @Schema(implementation = TransferResponse.class),
                examples = @ExampleObject(value = "{\"transaction_id\":\"2eeecae9-c925-4dad-9d89-640c0f9e70db\",\"status\":\"COMPLETED\"}")
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Validation or malformed request error",
            content = @Content(
                schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"timestamp\":\"2026-03-17T18:45:32.114Z\",\"status\":400,\"error\":\"bad_request\",\"message\":\"amount must be greater than 0\",\"path\":\"/transactions/transfer\",\"correlation_id\":\"corr-123\"}")
            )
        ),
        @ApiResponse(
            responseCode = "401",
            description = "Authentication required or invalid API key",
            content = @Content(
                schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"timestamp\":\"2026-03-17T18:45:32.114Z\",\"status\":401,\"error\":\"unauthorized\",\"message\":\"authentication is required\",\"path\":\"/transactions/transfer\",\"correlation_id\":\"corr-123\"}")
            )
        ),
        @ApiResponse(
            responseCode = "403",
            description = "API key lacks write permission",
            content = @Content(
                schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"timestamp\":\"2026-03-17T18:45:32.114Z\",\"status\":403,\"error\":\"forbidden\",\"message\":\"access is forbidden\",\"path\":\"/transactions/transfer\",\"correlation_id\":\"corr-123\"}")
            )
        ),
        @ApiResponse(
            responseCode = "409",
            description = "Idempotency key reused with a conflicting payload",
            content = @Content(
                schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"timestamp\":\"2026-03-17T18:45:32.114Z\",\"status\":409,\"error\":\"conflict\",\"message\":\"idempotency key already used with different payload\",\"path\":\"/transactions/transfer\",\"correlation_id\":\"corr-123\"}")
            )
        ),
        @ApiResponse(
            responseCode = "422",
            description = "Business rule violation",
            content = @Content(
                schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"timestamp\":\"2026-03-17T18:45:32.114Z\",\"status\":422,\"error\":\"unprocessable_entity\",\"message\":\"insufficient funds\",\"path\":\"/transactions/transfer\",\"correlation_id\":\"corr-123\"}")
            )
        ),
        @ApiResponse(
            responseCode = "429",
            description = "Write rate limit exceeded",
            content = @Content(
                schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"timestamp\":\"2026-03-17T18:45:32.114Z\",\"status\":429,\"error\":\"too_many_requests\",\"message\":\"rate limit exceeded for write endpoints\",\"path\":\"/transactions/transfer\",\"correlation_id\":\"corr-123\"}")
            )
        )
    })
    public TransferResponse transfer(@Valid @RequestBody TransferRequest request) {
        TransferResult result = transferService.transfer(request.toCommand());
        return TransferResponse.from(result);
    }
}
