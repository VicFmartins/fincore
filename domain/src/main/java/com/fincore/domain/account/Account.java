package com.fincore.domain.account;

import java.util.Objects;
import java.util.UUID;

public final class Account {
    private final UUID id;
    private final long balance;

    public Account(UUID id, long balance) {
        this.id = Objects.requireNonNull(id, "id");
        if (balance < 0) {
            throw new IllegalArgumentException("balance cannot be negative");
        }
        this.balance = balance;
    }

    public static Account createNew(UUID id) {
        return new Account(id, 0L);
    }

    public UUID getId() {
        return id;
    }

    public long getBalance() {
        return balance;
    }

    public Account credit(long amount) {
        validateAmount(amount);
        return new Account(id, balance + amount);
    }

    public Account debit(long amount) {
        validateAmount(amount);
        long newBalance = balance - amount;
        if (newBalance < 0) {
            throw new IllegalArgumentException("insufficient funds");
        }
        return new Account(id, newBalance);
    }

    private static void validateAmount(long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
    }
}
