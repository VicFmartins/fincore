package com.fincore.infrastructure.persistence.adapter;

import com.fincore.application.ports.TransactionRepositoryPort;
import com.fincore.application.usecases.IdempotencyConflictException;
import com.fincore.domain.transaction.Transaction;
import com.fincore.infrastructure.persistence.entity.TransactionEntity;
import com.fincore.infrastructure.persistence.repository.TransactionJpaRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class TransactionPersistenceAdapter implements TransactionRepositoryPort {
    private final TransactionJpaRepository repository;

    public TransactionPersistenceAdapter(TransactionJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<Transaction> findById(UUID id) {
        return repository.findById(id).map(TransactionPersistenceAdapter::toDomain);
    }

    @Override
    public Optional<Transaction> findByIdempotencyKey(String idempotencyKey) {
        return repository.findByIdempotencyKey(idempotencyKey)
            .map(TransactionPersistenceAdapter::toDomain);
    }

    @Override
    public Transaction save(Transaction transaction) {
        TransactionEntity entity = repository.findById(transaction.getId())
            .orElseGet(() -> new TransactionEntity(transaction.getId()));
        apply(entity, transaction);
        try {
            return toDomain(repository.save(entity));
        } catch (DataIntegrityViolationException ex) {
            throw new IdempotencyConflictException("idempotency key already exists", ex);
        }
    }

    private static void apply(TransactionEntity entity, Transaction transaction) {
        entity.setSourceAccountId(transaction.getSourceAccountId());
        entity.setDestinationAccountId(transaction.getDestinationAccountId());
        entity.setAmount(transaction.getAmount());
        entity.setStatus(transaction.getStatus().name());
        entity.setIdempotencyKey(transaction.getIdempotencyKey());
        entity.setCreatedAt(transaction.getCreatedAt());
        entity.setUpdatedAt(transaction.getUpdatedAt());
    }

    private static Transaction toDomain(TransactionEntity entity) {
        return new Transaction(
            entity.getId(),
            entity.getSourceAccountId(),
            entity.getDestinationAccountId(),
            entity.getAmount(),
            entity.getIdempotencyKey(),
            com.fincore.domain.transaction.TransactionStatus.valueOf(entity.getStatus()),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }
}
