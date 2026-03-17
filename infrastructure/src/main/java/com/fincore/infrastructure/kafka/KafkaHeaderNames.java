package com.fincore.infrastructure.kafka;

public final class KafkaHeaderNames {
    public static final String EVENT_ID = "x-event-id";
    public static final String AGGREGATE_ID = "x-aggregate-id";
    public static final String AGGREGATE_TYPE = "x-aggregate-type";
    public static final String CORRELATION_ID = "x-correlation-id";

    private KafkaHeaderNames() {
    }
}
