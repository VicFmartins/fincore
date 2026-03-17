package com.fincore.domain.ledger;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class LedgerEntry {
    private final UUID id;
    private final UUID accountId;
    private final UUID transactionId;
    private final LedgerEntryType type;
    private final long amount;
    private final long balanceAfter;
    private final Instant createdAt;

    public LedgerEntry(UUID id,
                       UUID accountId,
                       UUID transactionId,
                       LedgerEntryType type,
                       long amount,
                       long balanceAfter,
                       Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.accountId = Objects.requireNonNull(accountId, "accountId");
        this.transactionId = Objects.requireNonNull(transactionId, "transactionId");
        this.type = Objects.requireNonNull(type, "type");
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        this.amount = amount;
        if (balanceAfter < 0) {
            throw new IllegalArgumentException("balanceAfter cannot be negative");
        }
        this.balanceAfter = balanceAfter;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    public UUID getId() {
        return id;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public UUID getTransactionId() {
        return transactionId;
    }

    public LedgerEntryType getType() {
        return type;
    }

    public long getAmount() {
        return amount;
    }

    public long getBalanceAfter() {
        return balanceAfter;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
