package org.project.loslite.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Audit trail generik/polymorphic — mencatat perubahan pada entity APA PUN
 * (LoanApplication, Applicant, dst) lewat entityType + entityId, TANPA FK constraint
 * ke tabel spesifik. Ini pola yang lazim untuk audit table generik: satu tabel
 * menangani semua entity, bukan bikin audit_log_loan_application, audit_log_applicant, dst.
 */
@Entity
@Table(name = "audit_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

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

    @PrePersist
    void onCreate() {
        this.performedAt = Instant.now();
    }
}
