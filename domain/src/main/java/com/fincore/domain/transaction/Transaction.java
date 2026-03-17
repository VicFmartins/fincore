package com.fincore.domain.transaction;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class Transaction {
    private final UUID id;
    private final UUID sourceAccountId;
    private final UUID destinationAccountId;
    private final long amount;
    private final String idempotencyKey;
    private final TransactionStatus status;
    private final Instant createdAt;
    private final Instant updatedAt;

    public Transaction(UUID id,
                       UUID sourceAccountId,
                       UUID destinationAccountId,
                       long amount,
                       String idempotencyKey,
                       TransactionStatus status,
                       Instant createdAt,
                       Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.sourceAccountId = Objects.requireNonNull(sourceAccountId, "sourceAccountId");
        this.destinationAccountId = Objects.requireNonNull(destinationAccountId, "destinationAccountId");
        if (sourceAccountId.equals(destinationAccountId)) {
            throw new IllegalArgumentException("source and destination must differ");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        this.amount = amount;
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey cannot be blank");
        }
        this.idempotencyKey = idempotencyKey;
        this.status = Objects.requireNonNull(status, "status");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public static Transaction createPending(UUID id,
                                            UUID sourceAccountId,
                                            UUID destinationAccountId,
                                            long amount,
                                            String idempotencyKey,
                                            Instant now) {
        return new Transaction(
            id,
            sourceAccountId,
            destinationAccountId,
            amount,
            idempotencyKey,
            TransactionStatus.PENDING,
            now,
            now
        );
    }

    public Transaction withStatus(TransactionStatus target, Instant updatedAt) {
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (!status.canTransitionTo(target)) {
            throw new IllegalStateException("invalid status transition: " + status + " -> " + target);
        }
        return new Transaction(
            id,
            sourceAccountId,
            destinationAccountId,
            amount,
            idempotencyKey,
            target,
            createdAt,
            updatedAt
        );
    }

    public UUID getId() {
        return id;
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

    public TransactionStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
