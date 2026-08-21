package org.project.loslite.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.project.loslite.domain.enums.LoanStatus;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Satu pengajuan pinjaman. monthly_income & monthly_debt_obligation sengaja disimpan
 * DI SINI (bukan di Applicant) karena datanya spesifik per pengajuan — bisa berubah
 * setiap kali applicant yang sama re-apply di lain waktu.
 */
@Entity
@Table(name = "loan_application")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoanApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "applicant_id", nullable = false)
    private Applicant applicant;

    @Column(name = "loan_amount_requested", nullable = false, precision = 15, scale = 2)
    private BigDecimal loanAmountRequested;

    @Column(name = "loan_tenor_months", nullable = false)
    private Integer loanTenorMonths;

    @Column(name = "purpose", length = 255)
    private String purpose;

    @Column(name = "monthly_income", nullable = false, precision = 15, scale = 2)
    private BigDecimal monthlyIncome;

    @Column(name = "monthly_debt_obligation", nullable = false, precision = 15, scale = 2)
    private BigDecimal monthlyDebtObligation;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private LoanStatus status;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.status == null) {
            this.status = LoanStatus.DRAFT;
        }
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
