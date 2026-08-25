package org.project.loslite.model;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.project.loslite.enums.DocumentType;
import org.project.loslite.enums.OcrStatus;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;

/**
 * Dokumen yang di-upload untuk satu LoanApplication (KTP, slip gaji, dst).
 * ocr_raw_result menyimpan hasil mentah dari Python OCR service (JSON string) —
 * berguna untuk debug kalau OCR salah baca, tanpa harus panggil ulang service Python.
 *
 * id + created_at/updated_at diwarisi dari BaseEntity (created_at/updated_at di sini
 * jadi kolom tambahan yang nggak dipakai - uploaded_at tetap sumber kebenaran waktu
 * upload, lihat catatan di BaseEntity.java).
 */
@Entity
@Table(name = "document")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class Document extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loan_application_id", nullable = false)
    private LoanApplication loanApplication;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 30)
    private DocumentType documentType;

    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    @Enumerated(EnumType.STRING)
    @Column(name = "ocr_status", nullable = false, length = 20)
    private OcrStatus ocrStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ocr_raw_result", columnDefinition = "JSON")
    private String ocrRawResult;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private Instant uploadedAt;

    // Nama method sengaja BEDA dari onCreate() milik BaseEntity - lihat catatan
    // di BaseEntity.java soal kenapa nama-nya nggak boleh sama.
    @PrePersist
    void applyUploadDefaults() {
        this.uploadedAt = Instant.now();
        if (this.ocrStatus == null) {
            this.ocrStatus = OcrStatus.PENDING;
        }
    }
}
