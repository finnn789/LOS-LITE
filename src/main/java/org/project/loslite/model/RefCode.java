package org.project.loslite.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Tabel referensi generik — pengganti enum Java (LoanStatus, DocumentType, OcrStatus,
 * UserRole, ScoreBucket, ScoringDecision, dst). Satu baris = satu "nilai enum lama",
 * dikelompokkan lewat groupCode supaya satu tabel bisa nampung semua kategori
 * sekaligus, bukan bikin tabel terpisah per kategori.
 * <p>
 * groupCode + code = pengganti (nama enum class + nama constant). Contoh:
 * groupCode="LOAN_STATUS", code="SUBMITTED" ≈ dulunya LoanStatus.SUBMITTED.
 * <p>
 * CATATAN: entity ini SENGAJA tidak divalidasi lewat DB-level FK ke kolom seperti
 * loan_application.status — karena kolom itu cuma simpan "code" tanpa "groupCode",
 * FK biasa nggak bisa mastiin code-nya dari grup yang benar (mis. mencegah
 * DOCUMENT_TYPE kepasang di kolom status). Validasi (groupCode, code) dilakukan di
 * application layer (RefCodeService), bukan di level database.
 * <p>
 * id + createdAt/updatedAt diwarisi dari BaseEntity.
 */
@Entity
@Table(
        name = "ref_code",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ref_code_group_code",
                columnNames = {"group_code", "code"}
        ))
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class RefCode extends BaseEntity {

    @Column(name = "group_code", nullable = false, length = 50)
    private String groupCode;

    @Column(name = "code", nullable = false, length = 50)
    private String code;

    @Column(name = "label", nullable = false, length = 150)
    private String label;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(name = "is_active", nullable = false)
    private Boolean active;

    // Nama method sengaja BEDA dari onCreate() milik BaseEntity - lihat catatan
    // di BaseEntity.java soal kenapa nama-nya nggak boleh sama.
    @PrePersist
    void applyDefaults() {
        if (this.sortOrder == null) {
            this.sortOrder = 0;
        }
        if (this.active == null) {
            this.active = true;
        }
    }
}
