package org.project.loslite.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.project.loslite.domain.enums.ScoreBucket;
import org.project.loslite.domain.enums.ScoringDecision;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Histori hasil scoring (DTI + rule engine) untuk satu LoanApplication.
 * Sengaja versioned (1 LoanApplication : N ScoringResult) — kalau scoring dihitung
 * ulang (misal setelah dokumen di-upload ulang), histori lama TIDAK dihapus/overwrite.
 * Untuk ambil hasil terbaru: query ORDER BY calculated_at DESC LIMIT 1
 * (lihat ScoringResultRepository#findFirstByLoanApplicationIdOrderByCalculatedAtDesc).
 */
@Entity
@Table(name = "scoring_result")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScoringResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loan_application_id", nullable = false)
    private LoanApplication loanApplication;

    // Rasio 0.0000 - 1.0000 (misal 0.3542 = 35.42%)
    @Column(name = "dti_ratio", nullable = false, precision = 5, scale = 4)
    private BigDecimal dtiRatio;

    @Enumerated(EnumType.STRING)
    @Column(name = "score_bucket", nullable = false, length = 20)
    private ScoreBucket scoreBucket;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision", nullable = false, length = 20)
    private ScoringDecision decision;

    // Jejak rule mana saja yang "menyala" saat scoring — untuk explainability,
    // supaya keputusan approve/reject bisa dijelaskan, bukan black-box.
    @Column(name = "rule_trace", columnDefinition = "JSON")
    private String ruleTrace;

    @Column(name = "calculated_at", nullable = false, updatable = false)
    private Instant calculatedAt;

    @PrePersist
    void onCreate() {
        this.calculatedAt = Instant.now();
    }
}
