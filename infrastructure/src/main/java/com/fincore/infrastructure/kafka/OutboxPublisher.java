package com.fincore.infrastructure.kafka;

import com.fincore.infrastructure.observability.CorrelationContext;
import com.fincore.infrastructure.observability.FincoreMetrics;
import com.fincore.infrastructure.persistence.entity.OutboxEntity;
import com.fincore.infrastructure.persistence.repository.OutboxJpaRepository;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

@Component
public class OutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxJpaRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final FincoreMetrics fincoreMetrics;

    public OutboxPublisher(OutboxJpaRepository outboxRepository,
                           KafkaTemplate<String, String> kafkaTemplate,
                           FincoreMetrics fincoreMetrics) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.fincoreMetrics = fincoreMetrics;
    }

    @Transactional
    public int publishPending(int batchSize) {
        List<OutboxEntity> batch = outboxRepository.findBatchForPublish(batchSize);
        if (batch.isEmpty()) {
            return 0;
        }

        log.atInfo()
            .setMessage("outbox.publish.batch.started")
            .addKeyValue("batch_size", batch.size())
            .log();

        for (OutboxEntity message : batch) {
            publishOne(message);
        }

        Instant publishedAt = Instant.now();
        for (OutboxEntity message : batch) {
            message.setPublishedAt(publishedAt);
        }
        outboxRepository.saveAll(batch);
        log.atInfo()
            .setMessage("outbox.publish.batch.completed")
            .addKeyValue("batch_size", batch.size())
            .addKeyValue("publish_status", "SUCCESS")
            .log();
        return batch.size();
    }

    private void publishOne(OutboxEntity message) {
        String topic = message.getEventType();
        String key = message.getId().toString();
        String correlationId = message.getCorrelationId() == null || message.getCorrelationId().isBlank()
            ? UUID.randomUUID().toString()
            : message.getCorrelationId();

        try (CorrelationContext.Scope correlationScope = CorrelationContext.openScope(correlationId)) {
            log.atInfo()
                .setMessage("outbox.publish.attempt")
                .addKeyValue("correlation_id", correlationScope.correlationId())
                .addKeyValue("event_id", message.getId())
                .addKeyValue("transaction_id", message.getAggregateId())
                .addKeyValue("publish_status", "PENDING")
                .log();

            try {
                ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, message.getPayload());
                record.headers().add(new RecordHeader(KafkaHeaderNames.EVENT_ID, key.getBytes(StandardCharsets.UTF_8)));
                record.headers().add(new RecordHeader(KafkaHeaderNames.AGGREGATE_ID, message.getAggregateId().toString().getBytes(StandardCharsets.UTF_8)));
                record.headers().add(new RecordHeader(KafkaHeaderNames.AGGREGATE_TYPE, message.getAggregateType().getBytes(StandardCharsets.UTF_8)));
                record.headers().add(new RecordHeader(KafkaHeaderNames.CORRELATION_ID, correlationScope.correlationId().getBytes(StandardCharsets.UTF_8)));
                kafkaTemplate.send(record).get();
                fincoreMetrics.incrementOutboxPublished();
                log.atInfo()
                    .setMessage("outbox.publish.completed")
                    .addKeyValue("correlation_id", correlationScope.correlationId())
                    .addKeyValue("event_id", message.getId())
                    .addKeyValue("transaction_id", message.getAggregateId())
                    .addKeyValue("publish_status", "SUCCESS")
                    .log();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                fincoreMetrics.incrementOutboxPublishFailures();
                log.atWarn()
                    .setMessage("outbox.publish.failed")
                    .setCause(ex)
                    .addKeyValue("correlation_id", correlationScope.correlationId())
                    .addKeyValue("event_id", message.getId())
                    .addKeyValue("transaction_id", message.getAggregateId())
                    .addKeyValue("publish_status", "FAILED")
                    .log();
                throw new OutboxPublishException("publisher interrupted", ex);
            } catch (ExecutionException ex) {
                fincoreMetrics.incrementOutboxPublishFailures();
                log.atWarn()
                    .setMessage("outbox.publish.failed")
                    .setCause(ex)
                    .addKeyValue("correlation_id", correlationScope.correlationId())
                    .addKeyValue("event_id", message.getId())
                    .addKeyValue("transaction_id", message.getAggregateId())
                    .addKeyValue("publish_status", "FAILED")
                    .log();
                throw new OutboxPublishException("failed to publish outbox message", ex);
            }
        }
    }
}
