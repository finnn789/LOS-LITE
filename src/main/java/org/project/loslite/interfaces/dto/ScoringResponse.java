package org.project.loslite.interfaces.dto;

import org.project.loslite.domain.enums.ScoreBucket;
import org.project.loslite.domain.enums.ScoringDecision;
import org.project.loslite.application.service.RuleCheck;

import java.math.BigDecimal;
import java.util.List;

public record ScoringResponse(
        BigDecimal dtiRatio,
        ScoreBucket scoreBucket,
        ScoringDecision decision,
        List<RuleCheck> ruleTrace
) {
}
