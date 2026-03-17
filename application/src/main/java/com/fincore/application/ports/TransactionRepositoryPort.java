package com.fincore.application.ports;

import com.fincore.domain.transaction.Transaction;

import java.util.Optional;
import java.util.UUID;

public interface TransactionRepositoryPort {
    Optional<Transaction> findById(UUID id);

    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    Transaction save(Transaction transaction);
}
