package com.fincore.application.usecases;

public class IdempotencyConflictException extends RuntimeException {
    public IdempotencyConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
