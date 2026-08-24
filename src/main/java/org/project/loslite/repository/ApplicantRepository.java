package org.project.loslite.repository;

import org.project.loslite.model.Applicant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ApplicantRepository extends JpaRepository<Applicant, Long> {

    // Lookup/duplicate-check lewat nik_hash, BUKAN nik langsung (lihat catatan enkripsi di entity Applicant).
    Optional<Applicant> findByNikHash(String nikHash);

    boolean existsByNikHash(String nikHash);
}
