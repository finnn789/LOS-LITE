package org.project.loslite.dto;

import org.project.loslite.enums.ScoreBucket;
import org.project.loslite.enums.ScoringDecision;
import org.project.loslite.service.RuleCheck;

import java.math.BigDecimal;
import java.util.List;

public record ScoringResponse(
        BigDecimal dtiRatio,
        ScoreBucket scoreBucket,
        ScoringDecision decision,
        List<RuleCheck> ruleTrace
) {
}
