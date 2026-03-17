package com.fincore.application.ports;

import com.fincore.domain.ledger.LedgerEntry;

import java.util.List;
import java.util.UUID;

public interface LedgerEntryRepositoryPort {
    List<LedgerEntry> findByTransactionId(UUID transactionId);

    void saveAll(List<LedgerEntry> entries);
}
