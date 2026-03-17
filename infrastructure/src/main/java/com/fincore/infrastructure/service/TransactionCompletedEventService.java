package com.fincore.infrastructure.service;

import com.fincore.application.usecases.ProcessTransactionCompletedEventCommand;
import com.fincore.application.usecases.ProcessTransactionCompletedEventResult;
import com.fincore.application.usecases.ProcessTransactionCompletedEventUseCase;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionCompletedEventService {
    private final ProcessTransactionCompletedEventUseCase useCase;

    public TransactionCompletedEventService(ProcessTransactionCompletedEventUseCase useCase) {
        this.useCase = useCase;
    }

    @Transactional
    public ProcessTransactionCompletedEventResult process(ProcessTransactionCompletedEventCommand command) {
        return useCase.execute(command);
    }
}
