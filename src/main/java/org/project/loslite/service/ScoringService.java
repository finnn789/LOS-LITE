package org.project.loslite.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.project.loslite.enums.LoanStatus;
import org.project.loslite.enums.ScoringDecision;
import org.project.loslite.model.Applicant;
import org.project.loslite.model.LoanApplication;
import org.project.loslite.model.ScoringResult;
import org.project.loslite.persist.LoanApplicationPersist;
import org.project.loslite.persist.ScoringResultPersist;
import org.project.loslite.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Use-case orchestrator untuk proses scoring satu LoanApplication.
 * Tugasnya HANYA orkestrasi: ambil data dari repository -> panggil domain
 * service (RuleEngine) untuk logika bisnis murni -> simpan hasilnya -> update
 * status LoanApplication. Tidak ada aturan bisnis DTI/rule di sini sama sekali,
 * semua itu sudah ada di domain/service/RuleEngine.
 */
@Service
@RequiredArgsConstructor
public class ScoringService {

    private final LoanApplicationPersist loanApplicationPersist;
    private final ScoringResultPersist scoringResultPersist;
    private final RuleEngine ruleEngine;
    private final ObjectMapper objectMapper;
    private final LoanApplicationStatusService loanApplicationStatusService;

    @Transactional
    public ScoringOutcome score(Long loanApplicationId) {

        LoanApplication loanApplication = loanApplicationPersist.findById(loanApplicationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "LoanApplication dengan id " + loanApplicationId + " tidak ditemukan"));

        // Scoring cuma boleh jalan dari status DOCUMENT_VERIFICATION (sesuai state machine
        // di LoanStatusTransitionValidator). Transisi ini otomatis tercatat ke AuditLog juga.
        loanApplicationStatusService.changeStatus(loanApplication, LoanStatus.SCORING, null);

        // getApplicant() memicu lazy-load ke tabel applicant. Ini AMAN dipanggil
        // di sini karena method ini @Transactional -> session Hibernate masih terbuka.
        Applicant applicant = loanApplication.getApplicant();

        ScoringOutcome outcome = ruleEngine.evaluate(applicant, loanApplication);

        saveScoringResult(loanApplication, outcome);
        applyDecisionToLoanStatus(loanApplication, outcome.decision());

        return outcome;
    }

    private void saveScoringResult(LoanApplication loanApplication, ScoringOutcome outcome) {
        ScoringResult scoringResult = ScoringResult.builder()
                .loanApplication(loanApplication)
                .dtiRatio(outcome.dtiRatio())
                .scoreBucket(outcome.scoreBucket())
                .decision(outcome.decision())
                .ruleTrace(toJson(outcome.ruleTrace()))
                .build();

        scoringResultPersist.save(scoringResult);
    }

    private void applyDecisionToLoanStatus(LoanApplication loanApplication, ScoringDecision decision) {
        switch (decision) {
            case APPROVE -> {
                // performedBy null -> perubahan status ini dipicu OTOMATIS oleh RuleEngine,
                // bukan aksi manual staff. LoanApplicationStatusService yang urus validasi
                // transisi (SCORING -> APPROVED itu valid, lihat LoanStatusTransitionValidator)
                // dan penulisan AuditLog.
                loanApplicationStatusService.changeStatus(loanApplication, LoanStatus.APPROVED, null);
                loanApplication.setDecidedAt(Instant.now());
                loanApplicationPersist.save(loanApplication);
            }
            case REJECT -> {
                loanApplicationStatusService.changeStatus(loanApplication, LoanStatus.REJECTED, null);
                loanApplication.setDecidedAt(Instant.now());
                loanApplicationPersist.save(loanApplication);
            }
            case MANUAL_REVIEW -> {
                // Sengaja TIDAK ubah status & TIDAK set decidedAt - pengajuan tetap
                // "menggantung" di status SCORING sampai OFFICER/ADMIN memutuskan
                // manual lewat endpoint terpisah (belum dibuat).
            }
        }
    }

    // List<RuleCheck> (Java object) -> String JSON, supaya bisa disimpan ke
    // kolom ruleTrace (tipe JSON di database).
    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            // Kegagalan serialize di sini artinya bug di struktur RuleCheck itu sendiri,
            // bukan masalah input user - wajar dilempar sebagai unchecked exception,
            // akan ketangkap GlobalExceptionHandler.handleGeneric() dan tercatat di log.
            throw new IllegalStateException("Gagal serialize ruleTrace ke JSON", e);
        }
    }
}
