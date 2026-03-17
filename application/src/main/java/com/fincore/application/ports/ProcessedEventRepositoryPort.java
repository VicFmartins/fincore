package com.fincore.application.ports;

import java.time.Instant;
import java.util.UUID;

public interface ProcessedEventRepositoryPort {
    boolean markAsProcessed(UUID eventId, Instant processedAt);
}
