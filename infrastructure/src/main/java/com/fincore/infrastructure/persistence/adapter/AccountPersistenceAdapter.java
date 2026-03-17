package com.fincore.infrastructure.persistence.adapter;

import com.fincore.application.ports.AccountRepositoryPort;
import com.fincore.domain.account.Account;
import com.fincore.infrastructure.persistence.entity.AccountEntity;
import com.fincore.infrastructure.persistence.repository.AccountJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class AccountPersistenceAdapter implements AccountRepositoryPort {
    private final AccountJpaRepository repository;

    public AccountPersistenceAdapter(AccountJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<Account> findById(UUID id) {
        return repository.findById(id).map(AccountPersistenceAdapter::toDomain);
    }

    @Override
    public Optional<Account> findByIdForUpdate(UUID id) {
        return repository.findByIdForUpdate(id).map(AccountPersistenceAdapter::toDomain);
    }

    @Override
    public Account save(Account account) {
        AccountEntity entity = repository.findById(account.getId())
            .orElseGet(() -> new AccountEntity(account.getId()));
        entity.setBalance(account.getBalance());
        return toDomain(repository.save(entity));
    }

    private static Account toDomain(AccountEntity entity) {
        return new Account(entity.getId(), entity.getBalance());
    }
}
