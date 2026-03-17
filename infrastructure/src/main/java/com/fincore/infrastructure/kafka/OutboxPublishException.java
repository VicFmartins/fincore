package com.fincore.infrastructure.kafka;

public class OutboxPublishException extends RuntimeException {
    public OutboxPublishException(String message, Throwable cause) {
        super(message, cause);
    }
}
