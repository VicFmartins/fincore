package com.fincore.infrastructure.observability;

import org.slf4j.MDC;

import java.util.UUID;

public final class CorrelationContext {
    public static final String MDC_KEY = "correlation_id";

    private CorrelationContext() {
    }

    public static Scope openScope(String candidateCorrelationId) {
        String previousValue = MDC.get(MDC_KEY);
        String correlationId = hasText(candidateCorrelationId) ? candidateCorrelationId : previousValue;
        if (!hasText(correlationId)) {
            correlationId = UUID.randomUUID().toString();
        }

        MDC.put(MDC_KEY, correlationId);
        return new Scope(correlationId, previousValue);
    }

    public static String currentCorrelationId() {
        return MDC.get(MDC_KEY);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public static final class Scope implements AutoCloseable {
        private final String correlationId;
        private final String previousValue;

        private Scope(String correlationId, String previousValue) {
            this.correlationId = correlationId;
            this.previousValue = previousValue;
        }

        public String correlationId() {
            return correlationId;
        }

        @Override
        public void close() {
            if (previousValue == null) {
                MDC.remove(MDC_KEY);
                return;
            }
            MDC.put(MDC_KEY, previousValue);
        }
    }
}
