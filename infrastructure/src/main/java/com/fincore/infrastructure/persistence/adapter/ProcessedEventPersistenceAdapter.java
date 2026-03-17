package com.fincore.infrastructure.persistence.adapter;

import com.fincore.application.ports.ProcessedEventRepositoryPort;
import com.fincore.infrastructure.persistence.repository.ProcessedEventJpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
public class ProcessedEventPersistenceAdapter implements ProcessedEventRepositoryPort {
    private final ProcessedEventJpaRepository repository;

    public ProcessedEventPersistenceAdapter(ProcessedEventJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean markAsProcessed(UUID eventId, Instant processedAt) {
        return repository.insert(eventId, processedAt) > 0;
    }
}
