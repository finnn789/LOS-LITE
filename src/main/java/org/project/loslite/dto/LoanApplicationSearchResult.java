package org.project.loslite.dto;

import org.project.loslite.enums.LoanStatus;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Hasil proyeksi {@code LoanApplicationRepository#search} (contoh query "berat" pakai
 * Blaze-Persistence): satu baris LoanApplication digabung dengan nama applicant (join).
 *
 * <p>Urutan &amp; tipe field di sini HARUS persis sama dengan konstruktor yang dipakai di
 * {@code selectNew(...)} pada {@code LoanApplicationRepository#search} - Blaze-Persistence
 * memanggil konstruktor ini langsung lewat reflection. Record biasa - TIDAK butuh
 * annotation processor apa pun (beda dari {@code @EntityView}), jadi tidak ada class
 * "Impl" yang harus ter-generate dulu sebelum bisa dipakai.
 */
public record LoanApplicationSearchResult(
        Long id,
        String applicantFullName,
        BigDecimal loanAmountRequested,
        LoanStatus status,
        Instant submittedAt
) {
}
