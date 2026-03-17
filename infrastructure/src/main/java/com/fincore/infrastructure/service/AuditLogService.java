package com.fincore.infrastructure.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincore.application.usecases.TransferCommand;
import com.fincore.application.usecases.TransferResult;
import com.fincore.domain.account.Account;
import com.fincore.infrastructure.persistence.entity.AuditLogEntity;
import com.fincore.infrastructure.persistence.repository.AuditLogJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class AuditLogService {
    private static final String ENTITY_TYPE_ACCOUNT = "ACCOUNT";
    private static final String ENTITY_TYPE_TRANSACTION = "TRANSACTION";
    private static final String ENTITY_TYPE_TRANSFER_REQUEST = "TRANSFER_REQUEST";
    private static final String ENTITY_TYPE_LOCAL_FUNDING = "LOCAL_FUNDING";

    private final AuditLogJpaRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public AuditLogService(AuditLogJpaRepository auditLogRepository, ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordAccountCreated(Account account, String correlationId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("account_id", account.getId());
        payload.put("balance", account.getBalance());

        insert(
            ENTITY_TYPE_ACCOUNT,
            account.getId().toString(),
            "CREATE",
            payload,
            correlationId
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordTransferRequested(TransferCommand command, String correlationId) {
        insert(
            ENTITY_TYPE_TRANSFER_REQUEST,
            command.getIdempotencyKey(),
            "TRANSFER_REQUESTED",
            transferPayload(command, null, null),
            correlationId
        );
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordTransferSucceeded(TransferCommand command, TransferResult result, String correlationId) {
        insert(
            ENTITY_TYPE_TRANSACTION,
            result.getTransactionId().toString(),
            "TRANSFER_SUCCEEDED",
            transferPayload(command, result.getTransactionId(), result.getStatus().name()),
            correlationId
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordTransferFailed(TransferCommand command, String correlationId, RuntimeException exception) {
        Map<String, Object> payload = transferPayload(command, null, null);
        payload.put("error_type", exception.getClass().getSimpleName());
        payload.put("error_message", exception.getMessage());

        insert(
            ENTITY_TYPE_TRANSFER_REQUEST,
            command.getIdempotencyKey(),
            "TRANSFER_FAILED",
            payload,
            correlationId
        );
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordLocalFundingSucceeded(UUID accountId,
                                            UUID transactionId,
                                            long amount,
                                            long balanceAfter,
                                            String idempotencyKey,
                                            boolean idempotentReplay,
                                            String correlationId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("account_id", accountId);
        payload.put("transaction_id", transactionId);
        payload.put("amount", amount);
        payload.put("balance_after", balanceAfter);
        payload.put("idempotency_key", idempotencyKey);
        payload.put("status", "COMPLETED");
        payload.put("idempotent_replay", idempotentReplay);

        insert(
            ENTITY_TYPE_LOCAL_FUNDING,
            transactionId.toString(),
            "LOCAL_FUND_SUCCEEDED",
            payload,
            correlationId
        );
    }

    private Map<String, Object> transferPayload(TransferCommand command, UUID transactionId, String status) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("source_account_id", command.getSourceAccountId());
        payload.put("destination_account_id", command.getDestinationAccountId());
        payload.put("amount", command.getAmount());
        payload.put("idempotency_key", command.getIdempotencyKey());
        if (transactionId != null) {
            payload.put("transaction_id", transactionId);
        }
        if (status != null) {
            payload.put("status", status);
        }
        return payload;
    }

    private void insert(String entityType,
                        String entityId,
                        String action,
                        Map<String, Object> payload,
                        String correlationId) {
        AuditLogEntity entity = new AuditLogEntity(UUID.randomUUID());
        entity.setEntityType(entityType);
        entity.setEntityId(entityId);
        entity.setAction(action);
        entity.setPayload(toJson(payload));
        entity.setCorrelationId(correlationId);
        entity.setCreatedAt(Instant.now());
        auditLogRepository.save(entity);
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to serialize audit payload", ex);
        }
    }
}
