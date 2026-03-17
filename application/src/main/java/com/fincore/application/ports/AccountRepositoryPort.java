package com.fincore.application.ports;

import com.fincore.domain.account.Account;

import java.util.Optional;
import java.util.UUID;

public interface AccountRepositoryPort {
    Optional<Account> findById(UUID id);

    Optional<Account> findByIdForUpdate(UUID id);

    Account save(Account account);
}
