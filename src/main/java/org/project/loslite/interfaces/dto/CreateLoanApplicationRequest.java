package org.project.loslite.interfaces.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CreateLoanApplicationRequest(

        @NotNull(message = "applicantId wajib diisi")
        Long applicantId,

        @NotNull(message = "Jumlah pinjaman wajib diisi")
        @DecimalMin(value = "0.01", message = "Jumlah pinjaman harus lebih dari 0")
        BigDecimal loanAmountRequested,

        @NotNull(message = "Tenor wajib diisi")
        @Min(value = 1, message = "Tenor minimal 1 bulan")
        Integer loanTenorMonths,

        String purpose,

        @NotNull(message = "Penghasilan bulanan wajib diisi")
        @DecimalMin(value = "0.01", message = "Penghasilan bulanan harus lebih dari 0")
        BigDecimal monthlyIncome,

        @NotNull(message = "Kewajiban utang bulanan wajib diisi")
        @DecimalMin(value = "0.00", message = "Kewajiban utang bulanan tidak boleh negatif")
        BigDecimal monthlyDebtObligation
) {
}
