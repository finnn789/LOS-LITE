package org.project.loslite.application.service;

import org.project.loslite.domain.enums.ScoreBucket;
import org.project.loslite.domain.enums.ScoringDecision;

import java.math.BigDecimal;
import java.util.List;

/**
 * Hasil akhir evaluasi RuleEngine untuk satu LoanApplication — inilah yang nanti
 * dipetakan (di application layer) jadi entity ScoringResult untuk disimpan.
 */
public record ScoringOutcome(
        BigDecimal dtiRatio,
        ScoreBucket scoreBucket,
        ScoringDecision decision,
        List<RuleCheck> ruleTrace
) {
}
