package com.fincore.infrastructure.kafka;

import java.util.UUID;

@FunctionalInterface
public interface TransactionCompletedConsumerHook {
    void afterHandling(UUID eventId);
}
