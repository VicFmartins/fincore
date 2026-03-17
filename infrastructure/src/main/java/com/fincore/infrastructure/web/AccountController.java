package com.fincore.infrastructure.web;

import com.fincore.domain.account.Account;
import com.fincore.infrastructure.service.AccountService;
import com.fincore.infrastructure.web.dto.AccountResponse;
import com.fincore.infrastructure.web.dto.CreateAccountRequest;
import com.fincore.infrastructure.web.dto.CreateAccountResponse;
import com.fincore.infrastructure.web.dto.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/accounts")
@Tag(name = "Accounts", description = "Account creation and read operations.")
public class AccountController {
    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    @Operation(
        summary = "Create account",
        description = "Creates a new wallet account with zero balance.",
        security = @SecurityRequirement(name = "apiKeyAuth")
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "201",
            description = "Account created",
            content = @Content(
                schema = @Schema(implementation = CreateAccountResponse.class),
                examples = @ExampleObject(value = "{\"id\":\"c6fb273c-44b4-4310-b13d-e6b8ec0f6ef0\",\"balance\":0}")
            )
        ),
        @ApiResponse(
            responseCode = "401",
            description = "Authentication required or invalid API key",
            content = @Content(
                schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"timestamp\":\"2026-03-17T18:45:32.114Z\",\"status\":401,\"error\":\"unauthorized\",\"message\":\"authentication is required\",\"path\":\"/accounts\",\"correlation_id\":\"corr-123\"}")
            )
        ),
        @ApiResponse(
            responseCode = "403",
            description = "API key lacks write permission",
            content = @Content(
                schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"timestamp\":\"2026-03-17T18:45:32.114Z\",\"status\":403,\"error\":\"forbidden\",\"message\":\"access is forbidden\",\"path\":\"/accounts\",\"correlation_id\":\"corr-123\"}")
            )
        ),
        @ApiResponse(
            responseCode = "429",
            description = "Write rate limit exceeded",
            content = @Content(
                schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"timestamp\":\"2026-03-17T18:45:32.114Z\",\"status\":429,\"error\":\"too_many_requests\",\"message\":\"rate limit exceeded for write endpoints\",\"path\":\"/accounts\",\"correlation_id\":\"corr-123\"}")
            )
        )
    })
    public ResponseEntity<CreateAccountResponse> createAccount(@Valid @RequestBody(required = false) CreateAccountRequest request) {
        Account account = accountService.createAccount();
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(account.getId())
            .toUri();
        return ResponseEntity.created(location).body(CreateAccountResponse.from(account));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get account", description = "Returns the current balance for a wallet account.")
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Account found",
            content = @Content(
                schema = @Schema(implementation = AccountResponse.class),
                examples = @ExampleObject(value = "{\"id\":\"c6fb273c-44b4-4310-b13d-e6b8ec0f6ef0\",\"balance\":100000}")
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid UUID",
            content = @Content(
                schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"timestamp\":\"2026-03-17T18:45:32.114Z\",\"status\":400,\"error\":\"bad_request\",\"message\":\"invalid UUID value for parameter id\",\"path\":\"/accounts/not-a-uuid\",\"correlation_id\":\"corr-123\"}")
            )
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Account not found",
            content = @Content(
                schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"timestamp\":\"2026-03-17T18:45:32.114Z\",\"status\":404,\"error\":\"not_found\",\"message\":\"account not found\",\"path\":\"/accounts/c6fb273c-44b4-4310-b13d-e6b8ec0f6ef0\",\"correlation_id\":\"corr-123\"}")
            )
        )
    })
    public AccountResponse getAccount(@PathVariable UUID id) {
        return AccountResponse.from(accountService.getAccount(id));
    }
}
