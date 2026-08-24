package org.project.loslite.repository;

import org.project.loslite.model.RefCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Skeleton dulu — query custom (filter/sort dinamis lewat Blaze-Persistence
 * CriteriaBuilderFactory, lihat QueryConfig) menyusul, diisi manual.
 */
public interface RefCodeRepository extends JpaRepository<RefCode, Long> {

    List<RefCode> findByGroupCodeAndActiveTrueOrderBySortOrderAsc(String groupCode);

    Optional<RefCode> findByGroupCodeAndCode(String groupCode, String code);

    boolean existsByGroupCodeAndCode(String groupCode, String code);
}
