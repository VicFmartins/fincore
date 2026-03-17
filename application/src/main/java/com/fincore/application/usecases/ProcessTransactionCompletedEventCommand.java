package com.fincore.application.usecases;

import java.util.UUID;

public record ProcessTransactionCompletedEventCommand(
    UUID eventId,
    UUID transactionId
) {
}
