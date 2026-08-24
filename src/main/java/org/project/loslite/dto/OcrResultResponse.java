package org.project.loslite.dto;

import org.project.loslite.dto.KtpFields;

import java.math.BigDecimal;

/**
 * Bentuk terstruktur dari Document.ocrRawResult (yang di database tersimpan
 * sebagai String JSON mentah). ktp berisi field KTP yang sudah diparsing rapi
 * oleh Python (nama, alamat, dst) - null kalau dokumennya bukan KTP. Teks OCR
 * mentah (rawText) SENGAJA tidak diekspos di sini - hanya hasil yang sudah
 * terstruktur yang relevan untuk konsumen API.
 */
public record OcrResultResponse(
        String nik,
        BigDecimal estimatedSalary,
        KtpFields ktp
) {
}
