package com.fincore.application.usecases;

import java.util.Objects;
import java.util.UUID;

public final class TransferCommand {
    private final UUID sourceAccountId;
    private final UUID destinationAccountId;
    private final long amount;
    private final String idempotencyKey;

    public TransferCommand(UUID sourceAccountId,
                           UUID destinationAccountId,
                           long amount,
                           String idempotencyKey) {
        this.sourceAccountId = Objects.requireNonNull(sourceAccountId, "sourceAccountId");
        this.destinationAccountId = Objects.requireNonNull(destinationAccountId, "destinationAccountId");
        if (sourceAccountId.equals(destinationAccountId)) {
            throw new IllegalArgumentException("source and destination must differ");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey cannot be blank");
        }
        this.amount = amount;
        this.idempotencyKey = idempotencyKey;
    }

    public UUID getSourceAccountId() {
        return sourceAccountId;
    }

    public UUID getDestinationAccountId() {
        return destinationAccountId;
    }

    public long getAmount() {
        return amount;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }
}
