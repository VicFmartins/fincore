package com.fincore.application.usecases;

import com.fincore.application.ports.AccountRepositoryPort;
import com.fincore.domain.account.Account;

import java.util.UUID;

public final class GetAccountUseCase {
    private final AccountRepositoryPort accountRepository;

    public GetAccountUseCase(AccountRepositoryPort accountRepository) {
        this.accountRepository = accountRepository;
    }

    public Account execute(UUID accountId) {
        return accountRepository.findById(accountId)
            .orElseThrow(() -> new NotFoundException("account not found: " + accountId));
    }
}
