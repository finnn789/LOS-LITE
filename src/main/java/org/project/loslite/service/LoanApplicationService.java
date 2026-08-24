package org.project.loslite.service;

import lombok.RequiredArgsConstructor;
import org.project.loslite.dto.CreateLoanApplicationCommand;
import org.project.loslite.enums.LoanStatus;
import org.project.loslite.model.Applicant;
import org.project.loslite.model.LoanApplication;
import org.project.loslite.repository.ApplicantRepository;
import org.project.loslite.repository.LoanApplicationRepository;
import org.project.loslite.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Use-case orchestrator untuk siklus hidup LoanApplication SEBELUM masuk scoring
 * (create draft -> submit -> document verification). Tahap SCORING dan seterusnya
 * ditangani ScoringService, karena butuh domain service (RuleEngine) yang berbeda urusan.
 */
@Service
@RequiredArgsConstructor
public class LoanApplicationService {

    private final LoanApplicationRepository loanApplicationRepository;
    private final ApplicantRepository applicantRepository;
    private final LoanApplicationStatusService loanApplicationStatusService;

    @Transactional
    public LoanApplication create(CreateLoanApplicationCommand command) {
        Applicant applicant = applicantRepository.findById(command.applicantId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Applicant dengan id " + command.applicantId() + " tidak ditemukan"));

        // Status TIDAK di-set di sini - LoanApplication.onCreate() (@PrePersist)
        // otomatis isi DRAFT kalau null, lihat LoanApplication.java.
        LoanApplication loanApplication = LoanApplication.builder()
                .applicant(applicant)
                .loanAmountRequested(command.loanAmountRequested())
                .loanTenorMonths(command.loanTenorMonths())
                .purpose(command.purpose())
                .monthlyIncome(command.monthlyIncome())
                .monthlyDebtObligation(command.monthlyDebtObligation())
                .build();

        return loanApplicationRepository.save(loanApplication);
    }

    @Transactional(readOnly = true)
    public LoanApplication getById(Long id) {
        return loanApplicationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "LoanApplication dengan id " + id + " tidak ditemukan"));
    }

    @Transactional
    public LoanApplication submit(Long id) {
        LoanApplication loanApplication = getById(id);

        loanApplicationStatusService.changeStatus(loanApplication, LoanStatus.SUBMITTED, null);
        loanApplication.setSubmittedAt(Instant.now());

        return loanApplicationRepository.save(loanApplication);
    }

    @Transactional
    public LoanApplication moveToDocumentVerification(Long id) {
        LoanApplication loanApplication = getById(id);

        return loanApplicationStatusService.changeStatus(loanApplication, LoanStatus.DOCUMENT_VERIFICATION, null);
    }
}
