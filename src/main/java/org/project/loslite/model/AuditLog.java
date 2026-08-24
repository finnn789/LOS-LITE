package org.project.loslite.model;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

/**
 * Audit trail generik/polymorphic — mencatat perubahan pada entity APA PUN
 * (LoanApplication, Applicant, dst) lewat entityType + entityId, TANPA FK constraint
 * ke tabel spesifik. Ini pola yang lazim untuk audit table generik: satu tabel
 * menangani semua entity, bukan bikin audit_log_loan_application, audit_log_applicant, dst.
 *
 * id + created_at/updated_at diwarisi dari BaseEntity (created_at/updated_at di sini
 * jadi kolom tambahan yang nggak dipakai - performed_at tetap sumber kebenaran
 * waktu kejadian, lihat catatan di BaseEntity.java).
 */
@Entity
@Table(name = "audit_log")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class AuditLog extends BaseEntity {

    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Column(name = "action", nullable = false, length = 50)
    private String action;

    @Column(name = "old_value", columnDefinition = "JSON")
    private String oldValue;

    @Column(name = "new_value", columnDefinition = "JSON")
    private String newValue;

    // Nullable: aksi otomatis dari sistem (misal auto-reject oleh rule engine)
    // tidak selalu punya actor manusia di baliknya.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "performed_by")
    private AppUser performedBy;

    @Column(name = "performed_at", nullable = false, updatable = false)
    private Instant performedAt;

    // Nama method sengaja BEDA dari onCreate() milik BaseEntity - lihat catatan
    // di BaseEntity.java soal kenapa nama-nya nggak boleh sama.
    @PrePersist
    void applyPerformedAt() {
        this.performedAt = Instant.now();
    }
}
