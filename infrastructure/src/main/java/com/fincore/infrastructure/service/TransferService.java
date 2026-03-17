package com.fincore.infrastructure.service;

import com.fincore.application.usecases.TransferCommand;
import com.fincore.application.usecases.TransferResult;
import com.fincore.infrastructure.observability.CorrelationContext;
import com.fincore.infrastructure.observability.FincoreMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class TransferService {
    private static final Logger log = LoggerFactory.getLogger(TransferService.class);

    private final TransferExecutionService transferExecutionService;
    private final AuditLogService auditLogService;
    private final FincoreMetrics fincoreMetrics;

    public TransferService(TransferExecutionService transferExecutionService,
                           AuditLogService auditLogService,
                           FincoreMetrics fincoreMetrics) {
        this.transferExecutionService = transferExecutionService;
        this.auditLogService = auditLogService;
        this.fincoreMetrics = fincoreMetrics;
    }

    public TransferResult transfer(TransferCommand command) {
        try (CorrelationContext.Scope correlationScope = CorrelationContext.openScope(null)) {
            log.atInfo()
                .setMessage("transfer.started")
                .addKeyValue("correlation_id", correlationScope.correlationId())
                .addKeyValue("source_account_id", command.getSourceAccountId())
                .addKeyValue("destination_account_id", command.getDestinationAccountId())
                .addKeyValue("amount", command.getAmount())
                .addKeyValue("idempotency_key", command.getIdempotencyKey())
                .addKeyValue("transfer_status", "STARTED")
                .log();
            auditLogService.recordTransferRequested(command, correlationScope.correlationId());

            try {
                TransferResult result = transferExecutionService.execute(command, correlationScope.correlationId());
                fincoreMetrics.incrementTransferRequestsSuccess();
                if (!result.isIdempotentReplay()) {
                    fincoreMetrics.incrementTransferExecutionsSuccess();
                }

                log.atInfo()
                    .setMessage("transfer.completed")
                    .addKeyValue("correlation_id", correlationScope.correlationId())
                    .addKeyValue("transaction_id", result.getTransactionId())
                    .addKeyValue("source_account_id", command.getSourceAccountId())
                    .addKeyValue("destination_account_id", command.getDestinationAccountId())
                    .addKeyValue("transfer_status", result.getStatus())
                    .addKeyValue("idempotent_replay", result.isIdempotentReplay())
                    .log();
                return result;
            } catch (RuntimeException ex) {
                fincoreMetrics.incrementTransferRequestsFailure();
                recordFailureAudit(command, correlationScope.correlationId(), ex);
                log.atWarn()
                    .setMessage("transfer.failed")
                    .setCause(ex)
                    .addKeyValue("correlation_id", correlationScope.correlationId())
                    .addKeyValue("source_account_id", command.getSourceAccountId())
                    .addKeyValue("destination_account_id", command.getDestinationAccountId())
                    .addKeyValue("amount", command.getAmount())
                    .addKeyValue("transfer_status", "FAILED")
                    .log();
                throw ex;
            }
        }
    }

    private void recordFailureAudit(TransferCommand command, String correlationId, RuntimeException exception) {
        try {
            auditLogService.recordTransferFailed(command, correlationId, exception);
        } catch (RuntimeException auditException) {
            log.atError()
                .setMessage("audit.transfer.failed")
                .setCause(auditException)
                .addKeyValue("correlation_id", correlationId)
                .addKeyValue("idempotency_key", command.getIdempotencyKey())
                .log();
            exception.addSuppressed(auditException);
        }
    }
}
