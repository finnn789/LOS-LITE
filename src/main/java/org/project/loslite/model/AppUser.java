package org.project.loslite.model;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.project.loslite.enums.UserRole;

/**
 * Staff internal (bukan nasabah) yang login via JWT untuk operasikan LOS.
 * Nama tabel sengaja "app_user", BUKAN "user" — "user" adalah reserved keyword di MySQL
 * dan akan bikin migration SQL error/butuh backtick di mana-mana kalau dipakai apa adanya.
 *
 * id + created_at/updated_at diwarisi dari BaseEntity.
 */
@Entity
@Table(name = "app_user", uniqueConstraints = {
        @UniqueConstraint(name = "uk_app_user_username", columnNames = "username")
})
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class AppUser extends BaseEntity {

    @Column(name = "username", nullable = false, length = 50)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private UserRole role;
}
