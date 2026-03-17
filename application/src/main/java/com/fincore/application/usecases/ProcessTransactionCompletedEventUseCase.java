package com.fincore.application.usecases;

import com.fincore.application.ports.ProcessedEventRepositoryPort;
import com.fincore.application.ports.TransactionRepositoryPort;
import com.fincore.domain.transaction.Transaction;
import com.fincore.domain.transaction.TransactionStatus;

import java.time.Instant;

public final class ProcessTransactionCompletedEventUseCase {
    private final ProcessedEventRepositoryPort processedEventRepository;
    private final TransactionRepositoryPort transactionRepository;

    public ProcessTransactionCompletedEventUseCase(ProcessedEventRepositoryPort processedEventRepository,
                                                   TransactionRepositoryPort transactionRepository) {
        this.processedEventRepository = processedEventRepository;
        this.transactionRepository = transactionRepository;
    }

    public ProcessTransactionCompletedEventResult execute(ProcessTransactionCompletedEventCommand command) {
        boolean marked = processedEventRepository.markAsProcessed(command.eventId(), Instant.now());
        if (!marked) {
            return ProcessTransactionCompletedEventResult.DUPLICATE;
        }

        Transaction transaction = transactionRepository.findById(command.transactionId())
            .orElseThrow(() -> new NotFoundException("transaction not found"));
        if (transaction.getStatus() != TransactionStatus.COMPLETED) {
            throw new BusinessRuleViolationException("transaction is not completed");
        }

        return ProcessTransactionCompletedEventResult.PROCESSED;
    }
}
