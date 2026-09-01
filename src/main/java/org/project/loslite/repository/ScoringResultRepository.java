package org.project.loslite.repository;

import com.blazebit.persistence.CriteriaBuilder;
import com.blazebit.persistence.CriteriaBuilderFactory;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.project.loslite.model.QScoringResult;
import org.project.loslite.model.ScoringResult;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Satu-satunya kelas akses data {@link ScoringResult}. SENGAJA class biasa, BUKAN interface
 * {@code extends JpaRepository} - lihat catatan pola yang sama di {@code ApplicantRepository}:
 * QueryDSL ({@link QScoringResult}) cuma sebagai generator
 * nama path type-safe, mesin eksekusi query tetap Blaze-Persistence {@link CriteriaBuilder}.
 * <p>
 * Method di sini sengaja tidak {@code @Transactional} - transaksi jadi tanggung jawab
 * Service pemanggil ({@code ScoringService}).
 */
@Repository
@RequiredArgsConstructor
public class ScoringResultRepository {

    private static final QScoringResult qScoringResult = new QScoringResult("sr");

    private final CriteriaBuilderFactory criteriaBuilderFactory;
    private final EntityManager entityManager;

    public List<ScoringResult> findByLoanApplicationIdOrderByCalculatedAtDesc(Long loanApplicationId) {
        CriteriaBuilder<ScoringResult> cb = criteriaBuilderFactory
                .create(entityManager, ScoringResult.class, "sr")
                .where(qScoringResult.loanApplication.id.toString()).eq(loanApplicationId)
                .orderByDesc(qScoringResult.calculatedAt.toString());

        return cb.getResultList();
    }
    public Optional<ScoringResult> findFirstByLoanApplicationIdOrderByCalculatedAtDesc(Long loanApplicationId) {
        CriteriaBuilder<ScoringResult> cb = criteriaBuilderFactory
                .create(entityManager, ScoringResult.class, "sr")
                .where(qScoringResult.loanApplication.id.toString()).eq(loanApplicationId)
                .orderByDesc(qScoringResult.calculatedAt.toString());

        return cb.setMaxResults(1).getResultList().stream().findFirst();
    }

    /**
     * Insert kalau {@code scoringResult.getId() == null}, update (merge) kalau sudah ada id.
     * Harus dipanggil dalam konteks transaksi ({@code @Transactional} di Service pemanggil).
     */
    public ScoringResult save(ScoringResult scoringResult) {
        if (scoringResult.getId() == null) {
            entityManager.persist(scoringResult);
            return scoringResult;
        }
        return entityManager.merge(scoringResult);
    }
}
