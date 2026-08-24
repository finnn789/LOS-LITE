package org.project.loslite.model;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;

/**
 * Master data nasabah. Satu Applicant (satu NIK) bisa punya banyak LoanApplication
 * seiring waktu (re-apply setelah ditolak/lunas) — lihat relasi 1:N di LoanApplication.
 *
 * id + created_at/updated_at diwarisi dari BaseEntity.
 */
@Entity
@Table(name = "applicant", uniqueConstraints = {
        @UniqueConstraint(name = "uk_applicant_nik_hash", columnNames = "nik_hash")
})
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class Applicant extends BaseEntity {

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(name = "nik", nullable = false, length = 255)
    private String nik;

    @Column(name = "nik_hash", nullable = false, length = 64)
    private String nikHash;

    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    @Column(name = "phone_number", nullable = false, length = 20)
    private String phoneNumber;

    @Column(name = "email", length = 150)
    private String email;

    @Column(name = "address", columnDefinition = "TEXT")
    private String address;
}
