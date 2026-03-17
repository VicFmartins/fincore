package com.fincore.infrastructure.web.dto;

import com.fincore.domain.account.Account;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(name = "CreateAccountResponse", description = "Created account details.")
public record CreateAccountResponse(
    @Schema(description = "Account identifier.", example = "c6fb273c-44b4-4310-b13d-e6b8ec0f6ef0")
    UUID id,
    @Schema(description = "Account balance in cents.", example = "0")
    long balance
) {
    public static CreateAccountResponse from(Account account) {
        return new CreateAccountResponse(account.getId(), account.getBalance());
    }
}
