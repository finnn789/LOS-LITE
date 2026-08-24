package org.project.loslite.model;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

/**
 * Base buat SEMUA entity — id (auto-increment) + created_at/updated_at (dikelola
 * otomatis lewat @PrePersist/@PreUpdate).
 *
 * @MappedSuperclass: field-field di sini di-inherit ke tabel masing-masing subclass
 * (BUKAN bikin tabel sendiri, beda dari @Entity biasa).
 *
 * CATATAN: beberapa entity (AppUser, Document, ScoringResult, AuditLog) sebenarnya
 * punya timestamp sendiri (created_at doang, atau uploaded_at/calculated_at/performed_at)
 * — created_at/updated_at dari base ini tetap ikut ada di tabel mereka juga
 * (kolom tambahan, sengaja diterima demi 1 base entity yang seragam ke semua entity).
 *
 * Kalau subclass butuh logic @PrePersist sendiri (default value field lain, dst),
 * WAJIB pakai nama method BEDA dari onCreate()/onUpdate() di sini — kalau namanya
 * sama persis, itu dianggap OVERRIDE (nge-timpa, bukan callback terpisah), jadi
 * timestamp created_at/updated_at otomatis ini malah nggak jalan.
 */
@MappedSuperclass
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
