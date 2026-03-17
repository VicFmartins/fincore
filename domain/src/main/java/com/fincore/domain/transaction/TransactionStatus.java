package com.fincore.domain.transaction;

public enum TransactionStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED;

    public boolean canTransitionTo(TransactionStatus target) {
        if (this == PENDING) {
            return target == PROCESSING || target == FAILED;
        }
        if (this == PROCESSING) {
            return target == COMPLETED || target == FAILED;
        }
        return false;
    }
}
