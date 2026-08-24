package org.project.loslite.service;

import org.project.loslite.enums.ScoreBucket;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Domain service murni Java (TIDAK ada import Spring) — sengaja begitu supaya bisa
 * di-unit-test tanpa perlu start Spring context, dan supaya jelas ini logika bisnis,
 * bukan infrastruktur.
 * <p>
 * Menghitung DTI (Debt-to-Income) ratio: seberapa besar porsi penghasilan bulanan
 * applicant habis untuk bayar cicilan utang (utang lama + cicilan pinjaman baru
 * yang sedang diajukan).
 */
public class DtiCalculator {

    // Threshold klasifikasi risiko. DTI < 30% = aman, 30-40% = perlu ditinjau,
    // > 40% = risiko tinggi. Angka ini konstanta bisnis, bukan konfigurasi teknis,
    // jadi wajar hardcode di sini (bukan application.yml).
    private static final BigDecimal LOW_RISK_THRESHOLD = new BigDecimal("0.30");
    private static final BigDecimal MEDIUM_RISK_THRESHOLD = new BigDecimal("0.40");

    // Bunga flat 1% per bulan untuk estimasi cicilan pinjaman baru.
    // Simplifikasi sengaja (bukan bunga anuitas/efektif) - project ini portfolio,
    // bukan aplikasi finansial produksi yang butuh presisi kalkulasi bunga bank.
    private static final BigDecimal FLAT_RATE_PER_MONTH = new BigDecimal("0.01");

    private static final int SCALE = 4;

    /**
     * @param monthlyIncome         penghasilan bulanan applicant
     * @param monthlyDebtObligation cicilan utang LAMA yang sudah ada (belum termasuk pinjaman baru)
     * @param loanAmountRequested   jumlah pinjaman yang sedang diajukan
     * @param loanTenorMonths       tenor pinjaman yang diajukan, dalam bulan
     */
    public DtiResult calculate(BigDecimal monthlyIncome, BigDecimal monthlyDebtObligation, BigDecimal loanAmountRequested, int loanTenorMonths) {

        BigDecimal newInstallment = calculateMonthlyInstallment(loanAmountRequested, loanTenorMonths);
        BigDecimal totalMonthlyDebt = monthlyDebtObligation.add(newInstallment);

        BigDecimal dtiRatio = totalMonthlyDebt.divide(monthlyIncome, SCALE, RoundingMode.HALF_UP);
        ScoreBucket scoreBucket = classify(dtiRatio);

        return new DtiResult(dtiRatio, newInstallment, scoreBucket);
    }

    /**
     * Cicilan bulanan pinjaman baru pakai skema flat rate:
     * total yang harus dibayar = pokok pinjaman + (pokok x bunga/bulan x tenor),
     * lalu dibagi rata ke tiap bulan tenor.
     */
    private BigDecimal calculateMonthlyInstallment(BigDecimal loanAmountRequested, int loanTenorMonths) {
        BigDecimal tenor = BigDecimal.valueOf(loanTenorMonths);

        BigDecimal totalInterest = loanAmountRequested.multiply(FLAT_RATE_PER_MONTH).multiply(tenor);

        BigDecimal totalPayable = loanAmountRequested.add(totalInterest);

        return totalPayable.divide(tenor, SCALE, RoundingMode.HALF_UP);
    }

    private ScoreBucket classify(BigDecimal dtiRatio) {
        if (dtiRatio.compareTo(LOW_RISK_THRESHOLD) < 0) {
            return ScoreBucket.LOW_RISK;
        }
        if (dtiRatio.compareTo(MEDIUM_RISK_THRESHOLD) <= 0) {
            return ScoreBucket.MEDIUM_RISK;
        }
        return ScoreBucket.HIGH_RISK;
    }
}
