package com.fincore.application.usecases;

import com.fincore.application.ports.AccountRepositoryPort;
import com.fincore.application.ports.LedgerEntryRepositoryPort;
import com.fincore.application.ports.OutboxMessage;
import com.fincore.application.ports.OutboxRepositoryPort;
import com.fincore.application.ports.TransactionRepositoryPort;
import com.fincore.domain.account.Account;
import com.fincore.domain.ledger.LedgerEntry;
import com.fincore.domain.ledger.LedgerEntryType;
import com.fincore.domain.transaction.Transaction;
import com.fincore.domain.transaction.TransactionStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class TransferUseCase {
    private static final String AGGREGATE_TYPE = "transaction";
    private static final String EVENT_CREATED = "transaction.created";
    private static final String EVENT_COMPLETED = "transaction.completed";
    private static final String EVENT_FAILED = "transaction.failed";

    private final AccountRepositoryPort accountRepository;
    private final TransactionRepositoryPort transactionRepository;
    private final LedgerEntryRepositoryPort ledgerEntryRepository;
    private final OutboxRepositoryPort outboxRepository;

    public TransferUseCase(AccountRepositoryPort accountRepository,
                           TransactionRepositoryPort transactionRepository,
                           LedgerEntryRepositoryPort ledgerEntryRepository,
                           OutboxRepositoryPort outboxRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.outboxRepository = outboxRepository;
    }

    public TransferResult execute(TransferCommand command) {
        Transaction existing = transactionRepository.findByIdempotencyKey(command.getIdempotencyKey())
            .orElse(null);
        if (existing != null) {
            return new TransferResult(existing.getId(), existing.getStatus(), true);
        }

        Instant now = Instant.now();
        Transaction pending = Transaction.createPending(
            UUID.randomUUID(),
            command.getSourceAccountId(),
            command.getDestinationAccountId(),
            command.getAmount(),
            command.getIdempotencyKey(),
            now
        );
        try {
            transactionRepository.save(pending);
        } catch (IdempotencyConflictException ex) {
            Transaction concurrent = transactionRepository.findByIdempotencyKey(command.getIdempotencyKey())
                .orElseThrow(() -> ex);
            return new TransferResult(concurrent.getId(), concurrent.getStatus(), true);
        }
        outboxRepository.save(buildOutboxMessage(pending, EVENT_CREATED, now));

        Transaction processing = pending.withStatus(TransactionStatus.PROCESSING, Instant.now());
        transactionRepository.save(processing);

        LockedAccounts locked = lockAccounts(command.getSourceAccountId(), command.getDestinationAccountId());
        Account source = locked.source();
        Account destination = locked.destination();

        if (source == null || destination == null) {
            throw failTransaction(processing, new NotFoundException("account not found"));
        }

        Account debited;
        try {
            debited = source.debit(command.getAmount());
        } catch (IllegalArgumentException ex) {
            throw failTransaction(processing, new BusinessRuleViolationException("insufficient funds"));
        }
        Account credited = destination.credit(command.getAmount());

        accountRepository.save(debited);
        accountRepository.save(credited);

        Instant ledgerTime = Instant.now();
        LedgerEntry debitEntry = new LedgerEntry(
            UUID.randomUUID(),
            debited.getId(),
            processing.getId(),
            LedgerEntryType.DEBIT,
            command.getAmount(),
            debited.getBalance(),
            ledgerTime
        );
        LedgerEntry creditEntry = new LedgerEntry(
            UUID.randomUUID(),
            credited.getId(),
            processing.getId(),
            LedgerEntryType.CREDIT,
            command.getAmount(),
            credited.getBalance(),
            ledgerTime
        );
        ledgerEntryRepository.saveAll(List.of(debitEntry, creditEntry));

        Transaction completed = processing.withStatus(TransactionStatus.COMPLETED, Instant.now());
        transactionRepository.save(completed);
        outboxRepository.save(buildOutboxMessage(completed, EVENT_COMPLETED, Instant.now()));

        return new TransferResult(completed.getId(), completed.getStatus(), false);
    }

    private RuntimeException failTransaction(Transaction processing, RuntimeException exception) {
        Instant failedAt = Instant.now();
        Transaction failed = processing.withStatus(TransactionStatus.FAILED, failedAt);
        transactionRepository.save(failed);
        outboxRepository.save(buildOutboxMessage(failed, EVENT_FAILED, failedAt));
        return exception;
    }

    private static OutboxMessage buildOutboxMessage(Transaction transaction, String eventType, Instant createdAt) {
        String payload = "{"
            + "\"transactionId\":\"" + transaction.getId() + "\","
            + "\"sourceAccountId\":\"" + transaction.getSourceAccountId() + "\","
            + "\"destinationAccountId\":\"" + transaction.getDestinationAccountId() + "\","
            + "\"amount\":" + transaction.getAmount() + ","
            + "\"status\":\"" + transaction.getStatus() + "\""
            + "}";
        return new OutboxMessage(
            UUID.randomUUID(),
            AGGREGATE_TYPE,
            transaction.getId(),
            eventType,
            payload,
            createdAt
        );
    }

    private LockedAccounts lockAccounts(UUID sourceAccountId, UUID destinationAccountId) {
        UUID firstId = sourceAccountId.compareTo(destinationAccountId) < 0 ? sourceAccountId : destinationAccountId;
        UUID secondId = sourceAccountId.equals(firstId) ? destinationAccountId : sourceAccountId;

        Account first = accountRepository.findByIdForUpdate(firstId).orElse(null);
        Account second = accountRepository.findByIdForUpdate(secondId).orElse(null);

        Account source = sourceAccountId.equals(firstId) ? first : second;
        Account destination = sourceAccountId.equals(firstId) ? second : first;
        return new LockedAccounts(source, destination);
    }

    private record LockedAccounts(Account source, Account destination) { }
}
