package org.project.loslite.dto;

import java.math.BigDecimal;

/**
 * Hasil ekstraksi OCR dari Python service. Nama field SENGAJA sama persis
 * dengan JSON yang dibalas Python (nik, estimatedSalary, rawText) - Jackson
 * cocokkan otomatis by name saat deserialize response, tidak perlu mapping manual.
 */
public record OcrResult(
        String nik,
        BigDecimal estimatedSalary,
        KtpFields ktp,
        String rawText
) {
}
