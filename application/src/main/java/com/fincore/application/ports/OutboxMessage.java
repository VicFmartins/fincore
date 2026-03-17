package com.fincore.application.ports;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class OutboxMessage {
    private final UUID id;
    private final String aggregateType;
    private final UUID aggregateId;
    private final String eventType;
    private final String payload;
    private final Instant createdAt;

    public OutboxMessage(UUID id,
                         String aggregateType,
                         UUID aggregateId,
                         String eventType,
                         String payload,
                         Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.aggregateType = requireNonBlank(aggregateType, "aggregateType");
        this.aggregateId = Objects.requireNonNull(aggregateId, "aggregateId");
        this.eventType = requireNonBlank(eventType, "eventType");
        this.payload = requireNonBlank(payload, "payload");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " cannot be blank");
        }
        return value;
    }

    public UUID getId() {
        return id;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public UUID getAggregateId() {
        return aggregateId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
