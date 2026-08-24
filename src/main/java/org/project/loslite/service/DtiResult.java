package org.project.loslite.service;

import org.project.loslite.enums.ScoreBucket;

import java.math.BigDecimal;

/**
 * Hasil perhitungan DTI: rasio + cicilan bulanan pinjaman baru (buat transparansi,
 * supaya bisa ditelusuri "cicilan barunya dihitung berapa" tanpa perlu hitung ulang manual)
 * + kategori risikonya sudah langsung diklasifikasikan.
 */
public record DtiResult(BigDecimal dtiRatio, BigDecimal newMonthlyInstallment, ScoreBucket scoreBucket) {
}
