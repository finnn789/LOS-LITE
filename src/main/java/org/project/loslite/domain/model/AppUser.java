package org.project.loslite.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.project.loslite.domain.enums.UserRole;

import java.time.Instant;

/**
 * Staff internal (bukan nasabah) yang login via JWT untuk operasikan LOS.
 * Nama tabel sengaja "app_user", BUKAN "user" — "user" adalah reserved keyword di MySQL
 * dan akan bikin migration SQL error/butuh backtick di mana-mana kalau dipakai apa adanya.
 */
@Entity
@Table(name = "app_user", uniqueConstraints = {
        @UniqueConstraint(name = "uk_app_user_username", columnNames = "username")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username", nullable = false, length = 50)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private UserRole role;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}
