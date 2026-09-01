package org.project.loslite.repository;

import com.blazebit.persistence.CriteriaBuilder;
import com.blazebit.persistence.CriteriaBuilderFactory;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.project.loslite.model.Applicant;
import org.project.loslite.model.QApplicant;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Satu-satunya kelas akses data {@link Applicant}. SENGAJA class biasa, BUKAN interface
 * {@code extends JpaRepository} - project ini sudah pindah semua repository dari Spring
 * Data JPA ke Blaze-Persistence murni. Nama property di {@code where(...)} diambil dari
 * {@link QApplicant} (Q-class hasil generate QueryDSL APT dari {@code @Entity Applicant})
 * supaya type-safe di compile-time - QueryDSL di sini CUMA dipakai sebagai generator nama
 * path, mesin eksekusi query tetap 100% Blaze-Persistence {@link CriteriaBuilder}.
 * <p>
 * Method di sini sengaja tidak {@code @Transactional} - transaksi jadi tanggung jawab
 * Service pemanggil ({@code ApplicantService}).
 */
@Repository
@RequiredArgsConstructor
public class ApplicantRepository {

    private static final QApplicant qApplicant = new QApplicant("a");

    private final CriteriaBuilderFactory criteriaBuilderFactory;
    private final EntityManager entityManager;

    // Lookup/duplicate-check lewat nik_hash, BUKAN nik langsung (lihat catatan enkripsi di entity Applicant).
    public Optional<Applicant> findByNikHash(String nikHash) {
        CriteriaBuilder<Applicant> cb = criteriaBuilderFactory
                .create(entityManager, Applicant.class, "a")
                .where(qApplicant.nikHash.toString()).eq(nikHash);

        return cb.getResultList().stream().findFirst();
    }

    public boolean existsByNikHash(String nikHash) {
        CriteriaBuilder<Applicant> cb = criteriaBuilderFactory
                .create(entityManager, Applicant.class, "a")
                .where(qApplicant.nikHash.toString()).eq(nikHash);

        return cb.getCountQuery().getSingleResult() > 0;
    }

    public Optional<Applicant> findById(Long id) {
        return Optional.ofNullable(entityManager.find(Applicant.class, id));
    }

    public List<Applicant> findAll() {
        return criteriaBuilderFactory
                .create(entityManager, Applicant.class, "a")
                .orderByAsc(qApplicant.id.toString())
                .getResultList();
    }

    /**
     * Insert kalau {@code applicant.getId() == null}, update (merge) kalau sudah ada id.
     * Harus dipanggil dalam konteks transaksi ({@code @Transactional} di Service pemanggil).
     */
    public Applicant save(Applicant applicant) {
        if (applicant.getId() == null) {
            entityManager.persist(applicant);
            return applicant;
        }
        return entityManager.merge(applicant);
    }
}
