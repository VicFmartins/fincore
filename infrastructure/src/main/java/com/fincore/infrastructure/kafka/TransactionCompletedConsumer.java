package com.fincore.infrastructure.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincore.application.usecases.ProcessTransactionCompletedEventCommand;
import com.fincore.application.usecases.ProcessTransactionCompletedEventResult;
import com.fincore.infrastructure.observability.CorrelationContext;
import com.fincore.infrastructure.observability.FincoreMetrics;
import com.fincore.infrastructure.service.TransactionCompletedEventService;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Headers;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

@Component
public class TransactionCompletedConsumer {
    private static final Logger log = LoggerFactory.getLogger(TransactionCompletedConsumer.class);

    private final ObjectMapper objectMapper;
    private final TransactionCompletedEventService transactionCompletedEventService;
    private final FincoreMetrics fincoreMetrics;
    private final TransactionCompletedConsumerHook hook;

    public TransactionCompletedConsumer(ObjectMapper objectMapper,
                                        TransactionCompletedEventService transactionCompletedEventService,
                                        FincoreMetrics fincoreMetrics,
                                        TransactionCompletedConsumerHook hook) {
        this.objectMapper = objectMapper;
        this.transactionCompletedEventService = transactionCompletedEventService;
        this.fincoreMetrics = fincoreMetrics;
        this.hook = hook;
    }

    @KafkaListener(
        topics = "${fincore.kafka.topics.transaction-completed:transaction.completed}",
        groupId = "${fincore.kafka.consumer.transaction-completed.group-id:fincore-transaction-completed}"
    )
    public void consume(String payload, @Headers Map<String, Object> headers) {
        UUID eventId = extractEventId(headers);
        TransactionCompletedPayload event = parsePayload(payload);
        String correlationId = extractStringHeader(headers, KafkaHeaderNames.CORRELATION_ID);

        try (CorrelationContext.Scope correlationScope = CorrelationContext.openScope(correlationId)) {
            log.atInfo()
                .setMessage("transaction.completed.received")
                .addKeyValue("correlation_id", correlationScope.correlationId())
                .addKeyValue("event_id", eventId)
                .addKeyValue("transaction_id", event.transactionId())
                .addKeyValue("transfer_status", "COMPLETED")
                .addKeyValue("deduplication_decision", "PENDING")
                .log();

            try {
                ProcessTransactionCompletedEventResult result = transactionCompletedEventService.process(
                    new ProcessTransactionCompletedEventCommand(eventId, event.transactionId())
                );
                if (result == ProcessTransactionCompletedEventResult.DUPLICATE) {
                    fincoreMetrics.incrementConsumerDeduplicated();
                } else {
                    fincoreMetrics.incrementConsumerProcessed();
                }

                log.atInfo()
                    .setMessage("transaction.completed.handled")
                    .addKeyValue("correlation_id", correlationScope.correlationId())
                    .addKeyValue("event_id", eventId)
                    .addKeyValue("transaction_id", event.transactionId())
                    .addKeyValue("transfer_status", "COMPLETED")
                    .addKeyValue("deduplication_decision", result.name())
                    .log();

                hook.afterHandling(eventId);
            } catch (RuntimeException ex) {
                log.atWarn()
                    .setMessage("transaction.completed.failed")
                    .setCause(ex)
                    .addKeyValue("correlation_id", correlationScope.correlationId())
                    .addKeyValue("event_id", eventId)
                    .addKeyValue("transaction_id", event.transactionId())
                    .addKeyValue("transfer_status", "COMPLETED")
                    .addKeyValue("deduplication_decision", "FAILED")
                    .log();
                throw ex;
            }
        }
    }

    private UUID extractEventId(Map<String, Object> headers) {
        String eventId = extractRequiredStringHeader(headers, KafkaHeaderNames.EVENT_ID);
        return UUID.fromString(eventId);
    }

    private String extractRequiredStringHeader(Map<String, Object> headers, String headerName) {
        String value = extractStringHeader(headers, headerName);
        if (value == null) {
            throw new IllegalArgumentException("missing " + headerName + " header");
        }
        return value;
    }

    private String extractStringHeader(Map<String, Object> headers, String headerName) {
        Object rawHeader = headers.get(headerName);
        if (rawHeader == null) {
            return null;
        }
        if (rawHeader instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        if (rawHeader instanceof String value) {
            return value;
        }
        if (rawHeader instanceof Header header) {
            return new String(header.value(), StandardCharsets.UTF_8);
        }
        throw new IllegalArgumentException("unsupported " + headerName + " header type: " + rawHeader.getClass().getName());
    }

    private TransactionCompletedPayload parsePayload(String payload) {
        try {
            return objectMapper.readValue(payload, TransactionCompletedPayload.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("invalid transaction.completed payload", ex);
        }
    }

    private record TransactionCompletedPayload(UUID transactionId) {
    }
}
