package org.project.loslite.service;

import com.blazebit.persistence.CriteriaBuilderFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.project.loslite.dto.CreateLoanApplicationCommand;
import org.project.loslite.enums.LoanStatus;
import org.project.loslite.exception.ResourceNotFoundException;
import org.project.loslite.model.Applicant;
import org.project.loslite.model.LoanApplication;
import org.project.loslite.model.QApplicant;
import org.project.loslite.model.QLoanApplication;
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

    @PersistenceContext
    private EntityManager em;

    private final CriteriaBuilderFactory configBuilder;
    private final LoanApplicationStatusService loanApplicationStatusService;

    @Transactional
    public LoanApplication create(CreateLoanApplicationCommand command) {

        var qa = new QApplicant("a");

        var applicants = configBuilder.create(em, Applicant.class)
                .from(Applicant.class, qa.getMetadata().getName())
                .where(qa.id.toString()).eq(command.applicantId())
                .setMaxResults(1)
                .getResultList();

        if (applicants.isEmpty()) {
            throw new ResourceNotFoundException(
                    "Applicant dengan id " + command.applicantId() + " tidak ditemukan");
        }

        var applicant = applicants.get(0);

        // Status TIDAK di-set di sini - LoanApplication.onCreate() (@PrePersist)
        // otomatis isi DRAFT kalau null, lihat LoanApplication.java.
        var loanApplication = LoanApplication.builder()
                .applicant(applicant)
                .loanAmountRequested(command.loanAmountRequested())
                .loanTenorMonths(command.loanTenorMonths())
                .purpose(command.purpose())
                .monthlyIncome(command.monthlyIncome())
                .monthlyDebtObligation(command.monthlyDebtObligation())
                .build();

        em.persist(loanApplication);

        return loanApplication;
    }

    @Transactional(readOnly = true)
    public LoanApplication getById(Long id) {

        var qp = new QLoanApplication("l");

        var res = configBuilder.create(em, LoanApplication.class)
                .from(LoanApplication.class, qp.getMetadata().getName())
                .where(qp.id.toString()).eq(id)
                .setMaxResults(1)
                .getResultList();

        if (res.isEmpty()) {
            throw new ResourceNotFoundException(
                    "LoanApplication dengan id " + id + " tidak ditemukan");
        }

        return res.get(0);
    }

    @Transactional
    public LoanApplication submit(Long id) {

        var loanApplication = getById(id);

        loanApplicationStatusService.changeStatus(loanApplication, LoanStatus.SUBMITTED, null);
        loanApplication.setSubmittedAt(Instant.now());

        // Tidak perlu save() - loanApplication managed, UPDATE otomatis lewat dirty checking.
        return loanApplication;
    }

    @Transactional
    public LoanApplication moveToDocumentVerification(Long id) {

        var loanApplication = getById(id);

        return loanApplicationStatusService.changeStatus(loanApplication, LoanStatus.DOCUMENT_VERIFICATION, null);
    }
}