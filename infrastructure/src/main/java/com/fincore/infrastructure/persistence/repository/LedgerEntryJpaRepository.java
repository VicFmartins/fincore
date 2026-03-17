package com.fincore.infrastructure.persistence.repository;

import com.fincore.infrastructure.persistence.entity.LedgerEntryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LedgerEntryJpaRepository extends JpaRepository<LedgerEntryEntity, UUID> {
    List<LedgerEntryEntity> findByTransactionId(UUID transactionId);
}
