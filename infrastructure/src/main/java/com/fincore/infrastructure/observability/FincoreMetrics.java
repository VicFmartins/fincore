package com.fincore.infrastructure.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class FincoreMetrics {
    public static final String TRANSFER_REQUESTS_SUCCESS = "fincore.transfer.requests.success";
    public static final String TRANSFER_REQUESTS_FAILURE = "fincore.transfer.requests.failure";
    public static final String TRANSFER_EXECUTIONS_SUCCESS = "fincore.transfer.executions.success";
    public static final String OUTBOX_PUBLISHED = "fincore.outbox.published";
    public static final String OUTBOX_PUBLISH_FAILURES = "fincore.outbox.publish.failures";
    public static final String CONSUMER_PROCESSED = "fincore.consumer.processed";
    public static final String CONSUMER_DEDUPLICATED = "fincore.consumer.deduplicated";

    private final Counter transferRequestsSuccess;
    private final Counter transferRequestsFailure;
    private final Counter transferExecutionsSuccess;
    private final Counter outboxPublished;
    private final Counter outboxPublishFailures;
    private final Counter consumerProcessed;
    private final Counter consumerDeduplicated;

    public FincoreMetrics(MeterRegistry meterRegistry) {
        this.transferRequestsSuccess = meterRegistry.counter(TRANSFER_REQUESTS_SUCCESS);
        this.transferRequestsFailure = meterRegistry.counter(TRANSFER_REQUESTS_FAILURE);
        this.transferExecutionsSuccess = meterRegistry.counter(TRANSFER_EXECUTIONS_SUCCESS);
        this.outboxPublished = meterRegistry.counter(OUTBOX_PUBLISHED);
        this.outboxPublishFailures = meterRegistry.counter(OUTBOX_PUBLISH_FAILURES);
        this.consumerProcessed = meterRegistry.counter(CONSUMER_PROCESSED);
        this.consumerDeduplicated = meterRegistry.counter(CONSUMER_DEDUPLICATED);
    }

    public void incrementTransferRequestsSuccess() {
        transferRequestsSuccess.increment();
    }

    public void incrementTransferRequestsFailure() {
        transferRequestsFailure.increment();
    }

    public void incrementTransferExecutionsSuccess() {
        transferExecutionsSuccess.increment();
    }

    public void incrementOutboxPublished() {
        outboxPublished.increment();
    }

    public void incrementOutboxPublishFailures() {
        outboxPublishFailures.increment();
    }

    public void incrementConsumerProcessed() {
        consumerProcessed.increment();
    }

    public void incrementConsumerDeduplicated() {
        consumerDeduplicated.increment();
    }
}
