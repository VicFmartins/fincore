package com.fincore.application.usecases;

import com.fincore.domain.transaction.TransactionStatus;

import java.util.Objects;
import java.util.UUID;

public final class TransferResult {
    private final UUID transactionId;
    private final TransactionStatus status;
    private final boolean idempotentReplay;

    public TransferResult(UUID transactionId, TransactionStatus status, boolean idempotentReplay) {
        this.transactionId = Objects.requireNonNull(transactionId, "transactionId");
        this.status = Objects.requireNonNull(status, "status");
        this.idempotentReplay = idempotentReplay;
    }

    public UUID getTransactionId() {
        return transactionId;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public boolean isIdempotentReplay() {
        return idempotentReplay;
    }
}
