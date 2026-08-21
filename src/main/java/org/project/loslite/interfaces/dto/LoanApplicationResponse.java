package org.project.loslite.interfaces.dto;

import org.project.loslite.domain.enums.LoanStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record LoanApplicationResponse(
        Long id,
        Long applicantId,
        BigDecimal loanAmountRequested,
        Integer loanTenorMonths,
        String purpose,
        BigDecimal monthlyIncome,
        BigDecimal monthlyDebtObligation,
        LoanStatus status,
        Instant submittedAt,
        Instant decidedAt
) {
}
