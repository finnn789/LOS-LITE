package org.project.loslite.domain.repository;

import org.project.loslite.domain.model.ScoringResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ScoringResultRepository extends JpaRepository<ScoringResult, Long> {

    List<ScoringResult> findByLoanApplicationIdOrderByCalculatedAtDesc(Long loanApplicationId);

    // Ambil hasil scoring TERBARU saja untuk satu loan application — dipakai
    // saat cek keputusan aktif tanpa perlu load seluruh histori.
    Optional<ScoringResult> findFirstByLoanApplicationIdOrderByCalculatedAtDesc(Long loanApplicationId);
}
