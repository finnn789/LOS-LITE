package org.project.loslite.dto;

import java.math.BigDecimal;

public record UpdateLoanApplicationCommand(
        Long id,
        BigDecimal loanAmountRequested,
        Integer loanTenorMonths,
        String purpose,
        BigDecimal monthlyIncome,
        BigDecimal monthlyDebtObligation
) {
}
