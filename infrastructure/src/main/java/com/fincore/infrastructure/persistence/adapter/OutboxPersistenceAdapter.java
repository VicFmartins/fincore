package com.fincore.infrastructure.persistence.adapter;

import com.fincore.application.ports.OutboxMessage;
import com.fincore.infrastructure.observability.CorrelationContext;
import com.fincore.application.ports.OutboxRepositoryPort;
import com.fincore.infrastructure.persistence.entity.OutboxEntity;
import com.fincore.infrastructure.persistence.repository.OutboxJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class OutboxPersistenceAdapter implements OutboxRepositoryPort {
    private final OutboxJpaRepository repository;

    public OutboxPersistenceAdapter(OutboxJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public void save(OutboxMessage message) {
        repository.save(toEntity(message));
    }

    @Override
    public void saveAll(List<OutboxMessage> messages) {
        repository.saveAll(messages.stream().map(OutboxPersistenceAdapter::toEntity).toList());
    }

    private static OutboxEntity toEntity(OutboxMessage message) {
        OutboxEntity entity = new OutboxEntity(message.getId());
        entity.setAggregateType(message.getAggregateType());
        entity.setAggregateId(message.getAggregateId());
        entity.setEventType(message.getEventType());
        entity.setPayload(message.getPayload());
        entity.setCorrelationId(CorrelationContext.currentCorrelationId());
        entity.setCreatedAt(message.getCreatedAt());
        return entity;
    }
}
