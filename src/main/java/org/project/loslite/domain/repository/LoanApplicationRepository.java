package org.project.loslite.domain.repository;

import org.project.loslite.domain.enums.LoanStatus;
import org.project.loslite.domain.model.LoanApplication;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LoanApplicationRepository extends JpaRepository<LoanApplication, Long> {

    // Histori pengajuan milik satu applicant (1 Applicant : N LoanApplication).
    List<LoanApplication> findByApplicantId(Long applicantId);

    List<LoanApplication> findByStatus(LoanStatus status);
}
