package com.fincore.infrastructure.persistence.adapter;

import com.fincore.application.ports.LedgerEntryRepositoryPort;
import com.fincore.domain.ledger.LedgerEntry;
import com.fincore.infrastructure.persistence.entity.LedgerEntryEntity;
import com.fincore.infrastructure.persistence.repository.LedgerEntryJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class LedgerEntryPersistenceAdapter implements LedgerEntryRepositoryPort {
    private final LedgerEntryJpaRepository repository;

    public LedgerEntryPersistenceAdapter(LedgerEntryJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<LedgerEntry> findByTransactionId(UUID transactionId) {
        return repository.findByTransactionId(transactionId).stream()
            .map(LedgerEntryPersistenceAdapter::toDomain)
            .toList();
    }

    @Override
    public void saveAll(List<LedgerEntry> entries) {
        List<LedgerEntryEntity> entities = entries.stream()
            .map(LedgerEntryPersistenceAdapter::toEntity)
            .toList();
        repository.saveAll(entities);
    }

    private static LedgerEntryEntity toEntity(LedgerEntry entry) {
        LedgerEntryEntity entity = new LedgerEntryEntity(entry.getId());
        entity.setAccountId(entry.getAccountId());
        entity.setTransactionId(entry.getTransactionId());
        entity.setEntryType(entry.getType().name());
        entity.setAmount(entry.getAmount());
        entity.setBalanceAfter(entry.getBalanceAfter());
        entity.setCreatedAt(entry.getCreatedAt());
        return entity;
    }

    private static LedgerEntry toDomain(LedgerEntryEntity entity) {
        return new LedgerEntry(
            entity.getId(),
            entity.getAccountId(),
            entity.getTransactionId(),
            com.fincore.domain.ledger.LedgerEntryType.valueOf(entity.getEntryType()),
            entity.getAmount(),
            entity.getBalanceAfter(),
            entity.getCreatedAt()
        );
    }
}
