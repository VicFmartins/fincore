package com.fincore.infrastructure.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "fincore.outbox.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxScheduler {
    private static final Logger log = LoggerFactory.getLogger(OutboxScheduler.class);

    private final OutboxPublisher publisher;
    private final int batchSize;

    public OutboxScheduler(OutboxPublisher publisher,
                           @Value("${fincore.outbox.batch-size:50}") int batchSize) {
        this.publisher = publisher;
        this.batchSize = batchSize;
    }

    @Scheduled(
        fixedDelayString = "${fincore.outbox.fixed-delay:2000}",
        initialDelayString = "${fincore.outbox.initial-delay:1000}"
    )
    public void publishBatch() {
        try {
            int published;
            do {
                published = publisher.publishPending(batchSize);
            } while (published == batchSize);
        } catch (Exception ex) {
            log.warn("Outbox publish failed; will retry on next schedule", ex);
        }
    }
}
