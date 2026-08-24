package org.project.loslite.dto;

import org.project.loslite.enums.LoanStatus;

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
