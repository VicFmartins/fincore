package com.fincore.infrastructure.web.dto;

import com.fincore.domain.account.Account;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(name = "AccountResponse", description = "Account read model.")
public record AccountResponse(
    @Schema(description = "Account identifier.", example = "c6fb273c-44b4-4310-b13d-e6b8ec0f6ef0")
    UUID id,
    @Schema(description = "Current account balance in cents.", example = "100000")
    long balance
) {
    public static AccountResponse from(Account account) {
        return new AccountResponse(account.getId(), account.getBalance());
    }
}
