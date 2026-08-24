package org.project.loslite.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.project.loslite.enums.LoanStatus;
import org.project.loslite.model.AppUser;
import org.project.loslite.model.AuditLog;
import org.project.loslite.model.LoanApplication;
import org.project.loslite.repository.AuditLogRepository;
import org.project.loslite.repository.LoanApplicationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * SATU-SATUNYA pintu masuk untuk mengubah status LoanApplication di seluruh aplikasi.
 * Service lain (ScoringService, DocumentService, dst) TIDAK BOLEH panggil
 * loanApplication.setStatus(...) langsung - wajib lewat sini, supaya:
 * 1. Transisi selalu divalidasi (lewat LoanStatusTransitionValidator)
 * 2. Setiap perubahan status SELALU tercatat di AuditLog, tanpa kecuali
 */
@Service
@RequiredArgsConstructor
public class LoanApplicationStatusService {

    private final LoanApplicationRepository loanApplicationRepository;
    private final AuditLogRepository auditLogRepository;
    private final LoanStatusTransitionValidator transitionValidator;
    private final ObjectMapper objectMapper;

    /**
     * @param performedBy nullable - null berarti perubahan status dipicu otomatis oleh
     *                     sistem (misal RuleEngine auto-reject), bukan aksi manual staff.
     */
    @Transactional
    public LoanApplication changeStatus(LoanApplication loanApplication, LoanStatus newStatus, AppUser performedBy) {
        LoanStatus oldStatus = loanApplication.getStatus();

        transitionValidator.validateTransition(oldStatus, newStatus);

        loanApplication.setStatus(newStatus);
        LoanApplication saved = loanApplicationRepository.save(loanApplication);

        writeAuditLog(saved, oldStatus, newStatus, performedBy);

        return saved;
    }

    private void writeAuditLog(LoanApplication loanApplication, LoanStatus oldStatus, LoanStatus newStatus, AppUser performedBy) {
        AuditLog auditLog = AuditLog.builder()
                .entityType("LoanApplication")
                .entityId(loanApplication.getId())
                .action("STATUS_CHANGE")
                .oldValue(toJson(oldStatus))
                .newValue(toJson(newStatus))
                .performedBy(performedBy)
                .build();

        auditLogRepository.save(auditLog);
    }

    private String toJson(LoanStatus status) {
        try {
            return objectMapper.writeValueAsString(status);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Gagal serialize LoanStatus ke JSON", e);
        }
    }
}
