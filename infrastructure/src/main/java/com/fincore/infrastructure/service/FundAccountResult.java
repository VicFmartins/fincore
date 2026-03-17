package com.fincore.infrastructure.service;

import java.util.UUID;

public record FundAccountResult(
    UUID accountId,
    UUID transactionId,
    String status,
    long balance,
    boolean idempotentReplay
) {
}
