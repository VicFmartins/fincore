package com.fincore.application.usecases;

import com.fincore.application.ports.AccountRepositoryPort;
import com.fincore.domain.account.Account;

import java.util.UUID;

public final class CreateAccountUseCase {
    private final AccountRepositoryPort accountRepository;

    public CreateAccountUseCase(AccountRepositoryPort accountRepository) {
        this.accountRepository = accountRepository;
    }

    public Account execute() {
        Account account = Account.createNew(UUID.randomUUID());
        return accountRepository.save(account);
    }
}
