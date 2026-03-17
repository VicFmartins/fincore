package com.fincore.infrastructure.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
    name = "CreateAccountRequest",
    description = "Account creation payload. FinCore creates the account with a zero balance.",
    example = "{}"
)
public record CreateAccountRequest() {
}
