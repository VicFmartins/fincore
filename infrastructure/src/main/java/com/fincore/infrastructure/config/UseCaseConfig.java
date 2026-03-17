package com.fincore.infrastructure.config;

import com.fincore.application.ports.AccountRepositoryPort;
import com.fincore.application.ports.ProcessedEventRepositoryPort;
import com.fincore.application.ports.LedgerEntryRepositoryPort;
import com.fincore.application.ports.OutboxRepositoryPort;
import com.fincore.application.ports.TransactionRepositoryPort;
import com.fincore.application.usecases.CreateAccountUseCase;
import com.fincore.application.usecases.GetAccountUseCase;
import com.fincore.application.usecases.ProcessTransactionCompletedEventUseCase;
import com.fincore.application.usecases.TransferUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UseCaseConfig {
    @Bean
    public CreateAccountUseCase createAccountUseCase(AccountRepositoryPort accountRepository) {
        return new CreateAccountUseCase(accountRepository);
    }

    @Bean
    public GetAccountUseCase getAccountUseCase(AccountRepositoryPort accountRepository) {
        return new GetAccountUseCase(accountRepository);
    }

    @Bean
    public TransferUseCase transferUseCase(AccountRepositoryPort accountRepository,
                                           TransactionRepositoryPort transactionRepository,
                                           LedgerEntryRepositoryPort ledgerEntryRepository,
                                           OutboxRepositoryPort outboxRepository) {
        return new TransferUseCase(accountRepository, transactionRepository, ledgerEntryRepository, outboxRepository);
    }

    @Bean
    public ProcessTransactionCompletedEventUseCase processTransactionCompletedEventUseCase(
        ProcessedEventRepositoryPort processedEventRepository,
        TransactionRepositoryPort transactionRepository
    ) {
        return new ProcessTransactionCompletedEventUseCase(processedEventRepository, transactionRepository);
    }
}
