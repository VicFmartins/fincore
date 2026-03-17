package com.fincore.infrastructure.service;

import com.fincore.application.usecases.TransferCommand;
import com.fincore.application.usecases.TransferResult;
import com.fincore.application.usecases.TransferUseCase;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransferExecutionService {
    private final TransferUseCase transferUseCase;
    private final AuditLogService auditLogService;

    public TransferExecutionService(TransferUseCase transferUseCase, AuditLogService auditLogService) {
        this.transferUseCase = transferUseCase;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public TransferResult execute(TransferCommand command, String correlationId) {
        TransferResult result = transferUseCase.execute(command);
        auditLogService.recordTransferSucceeded(command, result, correlationId);
        return result;
    }
}
