package org.project.loslite.model;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.project.loslite.enums.ScoreBucket;
import org.project.loslite.enums.ScoringDecision;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Histori hasil scoring (DTI + rule engine) untuk satu LoanApplication.
 * Sengaja versioned (1 LoanApplication : N ScoringResult) — kalau scoring dihitung
 * ulang (misal setelah dokumen di-upload ulang), histori lama TIDAK dihapus/overwrite.
 * Untuk ambil hasil terbaru: query ORDER BY calculated_at DESC LIMIT 1
 * (lihat ScoringResultRepository#findFirstByLoanApplicationIdOrderByCalculatedAtDesc).
 *
 * id + created_at/updated_at diwarisi dari BaseEntity (created_at/updated_at di sini
 * jadi kolom tambahan yang nggak dipakai - calculated_at tetap sumber kebenaran
 * waktu kalkulasi, lihat catatan di BaseEntity.java).
 */
@Entity
@Table(name = "scoring_result")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class ScoringResult extends BaseEntity {

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

    // Nama method sengaja BEDA dari onCreate() milik BaseEntity - lihat catatan
    // di BaseEntity.java soal kenapa nama-nya nggak boleh sama.
    @PrePersist
    void applyCalculatedAt() {
        this.calculatedAt = Instant.now();
    }
}
