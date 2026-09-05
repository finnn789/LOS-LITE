package org.project.loslite.dto;

/**
 * Proyeksi ringan Applicant khusus buat {@code GET /applicants/niks} - cuma NIK, nama,
 * alamat (+ id buat referensi) - BUKAN {@link ApplicantSummaryResponse} (yang sengaja
 * tidak menyertakan NIK) atau {@link ApplicantResponse} (semua field, termasuk tanggal
 * lahir/telepon/email yang tidak relevan buat use-case ini).
 */
public record ApplicantNikResponse(
        Long id,
        String nik,
        String fullName,
        String address
) {
}
