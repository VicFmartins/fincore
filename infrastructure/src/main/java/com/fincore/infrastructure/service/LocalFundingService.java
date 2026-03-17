package com.fincore.infrastructure.service;

import com.fincore.application.ports.AccountRepositoryPort;
import com.fincore.application.ports.LedgerEntryRepositoryPort;
import com.fincore.application.ports.TransactionRepositoryPort;
import com.fincore.application.usecases.IdempotencyConflictException;
import com.fincore.application.usecases.NotFoundException;
import com.fincore.domain.account.Account;
import com.fincore.domain.ledger.LedgerEntry;
import com.fincore.domain.ledger.LedgerEntryType;
import com.fincore.domain.transaction.Transaction;
import com.fincore.domain.transaction.TransactionStatus;
import com.fincore.infrastructure.observability.CorrelationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Profile("local")
public class LocalFundingService {
    static final UUID LOCAL_TREASURY_ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-00000000f1c0");
    private static final long LOCAL_TREASURY_INITIAL_BALANCE = 9_000_000_000_000L;
    private static final String LOCAL_FUNDING_PREFIX = "local-fund:";

    private final AccountRepositoryPort accountRepository;
    private final TransactionRepositoryPort transactionRepository;
    private final LedgerEntryRepositoryPort ledgerEntryRepository;
    private final AuditLogService auditLogService;

    public LocalFundingService(AccountRepositoryPort accountRepository,
                               TransactionRepositoryPort transactionRepository,
                               LedgerEntryRepositoryPort ledgerEntryRepository,
                               AuditLogService auditLogService) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public FundAccountResult fund(UUID accountId, long amount, String idempotencyKey) {
        String localFundingIdempotencyKey = LOCAL_FUNDING_PREFIX + idempotencyKey;
        Transaction existing = transactionRepository.findByIdempotencyKey(localFundingIdempotencyKey).orElse(null);
        if (existing != null) {
            validateExistingFunding(existing, accountId, amount);
            Account fundedAccount = accountRepository.findById(accountId)
                .orElseThrow(() -> new NotFoundException("account not found"));
            return new FundAccountResult(
                accountId,
                existing.getId(),
                existing.getStatus().name(),
                fundedAccount.getBalance(),
                true
            );
        }

        Account treasury = ensureTreasuryAccount();
        Account target = accountRepository.findByIdForUpdate(accountId)
            .orElseThrow(() -> new NotFoundException("account not found"));

        Instant now = Instant.now();
        Transaction pending = Transaction.createPending(
            UUID.randomUUID(),
            treasury.getId(),
            target.getId(),
            amount,
            localFundingIdempotencyKey,
            now
        );

        try {
            transactionRepository.save(pending);
        } catch (IdempotencyConflictException ex) {
            Transaction concurrent = transactionRepository.findByIdempotencyKey(localFundingIdempotencyKey)
                .orElseThrow(() -> ex);
            validateExistingFunding(concurrent, accountId, amount);
            Account fundedAccount = accountRepository.findById(accountId)
                .orElseThrow(() -> new NotFoundException("account not found"));
            return new FundAccountResult(
                accountId,
                concurrent.getId(),
                concurrent.getStatus().name(),
                fundedAccount.getBalance(),
                true
            );
        }

        Transaction processing = pending.withStatus(TransactionStatus.PROCESSING, Instant.now());
        transactionRepository.save(processing);

        Account updatedTreasury = treasury.debit(amount);
        Account updatedTarget = target.credit(amount);
        accountRepository.save(updatedTreasury);
        accountRepository.save(updatedTarget);

        Instant ledgerTime = Instant.now();
        ledgerEntryRepository.saveAll(List.of(
            new LedgerEntry(
                UUID.randomUUID(),
                updatedTreasury.getId(),
                processing.getId(),
                LedgerEntryType.DEBIT,
                amount,
                updatedTreasury.getBalance(),
                ledgerTime
            ),
            new LedgerEntry(
                UUID.randomUUID(),
                updatedTarget.getId(),
                processing.getId(),
                LedgerEntryType.CREDIT,
                amount,
                updatedTarget.getBalance(),
                ledgerTime
            )
        ));

        Transaction completed = processing.withStatus(TransactionStatus.COMPLETED, Instant.now());
        transactionRepository.save(completed);
        recordFundingAudit(completed, updatedTarget.getBalance(), amount, idempotencyKey, false);

        return new FundAccountResult(
            accountId,
            completed.getId(),
            completed.getStatus().name(),
            updatedTarget.getBalance(),
            false
        );
    }

    private Account ensureTreasuryAccount() {
        return accountRepository.findByIdForUpdate(LOCAL_TREASURY_ACCOUNT_ID)
            .orElseGet(() -> accountRepository.save(new Account(LOCAL_TREASURY_ACCOUNT_ID, LOCAL_TREASURY_INITIAL_BALANCE)));
    }

    private void validateExistingFunding(Transaction transaction, UUID accountId, long amount) {
        if (!LOCAL_TREASURY_ACCOUNT_ID.equals(transaction.getSourceAccountId())
            || !accountId.equals(transaction.getDestinationAccountId())
            || amount != transaction.getAmount()) {
            throw new IdempotencyConflictException(
                "idempotency key already used with different payload",
                new IllegalStateException("conflicting local funding payload")
            );
        }
    }

    private void recordFundingAudit(Transaction transaction,
                                    long balanceAfter,
                                    long amount,
                                    String idempotencyKey,
                                    boolean idempotentReplay) {
        auditLogService.recordLocalFundingSucceeded(
            transaction.getDestinationAccountId(),
            transaction.getId(),
            amount,
            balanceAfter,
            idempotencyKey,
            idempotentReplay,
            CorrelationContext.currentCorrelationId()
        );
    }
}
