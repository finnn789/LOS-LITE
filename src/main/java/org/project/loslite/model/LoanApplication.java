package org.project.loslite.model;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.project.loslite.enums.LoanStatus;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Satu pengajuan pinjaman. monthly_income & monthly_debt_obligation sengaja disimpan
 * DI SINI (bukan di Applicant) karena datanya spesifik per pengajuan — bisa berubah
 * setiap kali applicant yang sama re-apply di lain waktu.
 *
 * id + created_at/updated_at diwarisi dari BaseEntity — biar nggak duplikat di tiap
 * entity.
 */
@Entity
@Table(name = "loan_application")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class LoanApplication extends BaseEntity {

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

    // Diisi saat submit() berhasil trigger business process di WORKFLOW-APP (lihat
    // LoanApplicationWorkflowService.submit()) - ketiganya nullable, TETAP null kalau
    // WORKFLOW-APP OFF (workflow-app.enabled=false) atau service-nya gagal dihubungi
    // (submit() tidak boleh gagal total cuma karena ini). businessKey yang dipakai untuk
    // lookup ke WORKFLOW-APP TIDAK disimpan di sini - deterministik, diturunkan dari id
    // lewat LoanApplicationWorkflowService#businessKeyOf(Long), bukan disimpan redundan.
    @Column(name = "workflow_trigger_id", length = 100)
    private String workflowTriggerId;

    @Column(name = "workflow_process_instance_id", length = 100)
    private String workflowProcessInstanceId;

    // Status TERAKHIR yang diketahui dari WORKFLOW-APP (STARTED/RUNNING/FAILED/COMPLETED/
    // CANCELLED/dst) - sengaja String bukan enum, WORKFLOW-APP eksplisit bilang treat
    // status-nya sebagai opaque value, bisa nambah varian baru kapan saja.
    @Column(name = "workflow_status", length = 30)
    private String workflowStatus;

    // Nama method sengaja BEDA dari onCreate() milik BaseEntity - keduanya tetap
    // sama-sama dipanggil JPA saat persist (satu dari superclass, satu dari sini).
    // Kalau namanya sama persis, itu jadi OVERRIDE (timestamp-nya nggak jalan lagi),
    // bukan 2 callback terpisah.
    @PrePersist
    void applyDefaultStatus() {
        if (this.status == null) {
            this.status = LoanStatus.DRAFT;
        }
    }
}
