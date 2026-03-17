package com.fincore.infrastructure.service;

import com.fincore.application.usecases.CreateAccountUseCase;
import com.fincore.application.usecases.GetAccountUseCase;
import com.fincore.domain.account.Account;
import com.fincore.infrastructure.observability.CorrelationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AccountService {
    private final CreateAccountUseCase createAccountUseCase;
    private final GetAccountUseCase getAccountUseCase;
    private final AuditLogService auditLogService;

    public AccountService(CreateAccountUseCase createAccountUseCase,
                          GetAccountUseCase getAccountUseCase,
                          AuditLogService auditLogService) {
        this.createAccountUseCase = createAccountUseCase;
        this.getAccountUseCase = getAccountUseCase;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public Account createAccount() {
        try (CorrelationContext.Scope correlationScope = CorrelationContext.openScope(null)) {
            Account account = createAccountUseCase.execute();
            auditLogService.recordAccountCreated(account, correlationScope.correlationId());
            return account;
        }
    }

    @Transactional(readOnly = true)
    public Account getAccount(UUID accountId) {
        return getAccountUseCase.execute(accountId);
    }
}
