package org.project.loslite.application.dto;

import java.math.BigDecimal;

/**
 * Data internal untuk membuat LoanApplication baru - dipetakan dari
 * CreateLoanApplicationRequest (interfaces layer) sebelum masuk ke service.
 * applicantId WAJIB sudah ada di database (pembuatan Applicant baru
 * adalah endpoint/fitur terpisah, belum dibuat).
 */
public record CreateLoanApplicationCommand(
        Long applicantId,
        BigDecimal loanAmountRequested,
        Integer loanTenorMonths,
        String purpose,
        BigDecimal monthlyIncome,
        BigDecimal monthlyDebtObligation
) {
}
