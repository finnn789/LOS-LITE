package org.project.loslite.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * BEDA dari CreateLoanApplicationRequest: tidak ada applicantId - mengganti pemilik
 * pengajuan bukan operasi "edit data", jadi sengaja tidak dibuka lewat endpoint ini.
 * status/submittedAt/decidedAt juga tidak ada di sini karena itu dikontrol state-machine
 * lewat endpoint submit/document-verification/scoring/review/disburse, bukan diedit bebas.
 * <p>
 * {@code @JsonIgnoreProperties(ignoreUnknown = true)}: form "Update" generic di FE LOS-LITE
 * (dynamic-form.js) ngirim ulang SEMUA field yang tadinya dibaca dari GET /{id} (termasuk
 * applicantId/status/submittedAt/decidedAt di atas, yang tidak ada di record ini) - field
 * asing itu harus diabaikan, bukan bikin request gagal.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UpdateLoanApplicationRequest(

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
