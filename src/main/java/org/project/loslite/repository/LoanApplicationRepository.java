package org.project.loslite.repository;

import com.blazebit.persistence.CriteriaBuilder;
import com.blazebit.persistence.CriteriaBuilderFactory;
import com.blazebit.persistence.PagedList;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.project.loslite.dto.LoanApplicationSearchCriteria;
import org.project.loslite.dto.LoanApplicationSearchResult;
import org.project.loslite.enums.LoanStatus;
import org.project.loslite.model.LoanApplication;
import org.project.loslite.model.QLoanApplication;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Satu-satunya kelas akses data {@link LoanApplication}. SENGAJA class biasa, BUKAN
 * interface {@code extends JpaRepository} (+ interface {@code Custom}/class {@code Impl}
 * terpisah seperti sebelumnya) - lihat catatan pola yang sama di
 * {@code ApplicantRepository}: QueryDSL
 * ({@link QLoanApplication}) cuma sebagai generator nama path type-safe untuk field
 * sederhana, mesin eksekusi query tetap 100% Blaze-Persistence {@link CriteriaBuilder}.
 * Query "berat" {@link #search} (join + filter dinamis + DTO projection lewat
 * {@code selectNew(...)} + pagination) tetap pakai path string manual untuk alias hasil
 * join ("app"), karena QueryDSL Q-class tidak merepresentasikan alias join custom seperti
 * itu.
 * <p>
 * Method di sini sengaja tidak {@code @Transactional} - transaksi jadi tanggung jawab
 * Service pemanggil ({@code LoanApplicationService}, {@code ScoringService}, dst).
 */
@Repository
@RequiredArgsConstructor
public class LoanApplicationRepository {

    private static final QLoanApplication qLoanApplication = new QLoanApplication("la");

    private final CriteriaBuilderFactory criteriaBuilderFactory;
    private final EntityManager entityManager;

    public Optional<LoanApplication> findById(Long id) {
        return Optional.ofNullable(entityManager.find(LoanApplication.class, id));
    }

    // Histori pengajuan milik satu applicant (1 Applicant : N LoanApplication).
    public List<LoanApplication> findByApplicantId(Long applicantId) {
        CriteriaBuilder<LoanApplication> cb = criteriaBuilderFactory
                .create(entityManager, LoanApplication.class, "la")
                .where(qLoanApplication.applicant.id.toString()).eq(applicantId)
                .orderByAsc(qLoanApplication.id.toString());

        return cb.getResultList();
    }

    public List<LoanApplication> findByStatus(LoanStatus status) {
        CriteriaBuilder<LoanApplication> cb = criteriaBuilderFactory
                .create(entityManager, LoanApplication.class, "la")
                .where(qLoanApplication.status.toString()).eq(status)
                .orderByAsc(qLoanApplication.id.toString());

        return cb.getResultList();
    }

    /**
     * Insert kalau {@code loanApplication.getId() == null}, update (merge) kalau sudah
     * ada id. Harus dipanggil dalam konteks transaksi ({@code @Transactional} di Service
     * pemanggil).
     */
    public LoanApplication save(LoanApplication loanApplication) {
        if (loanApplication.getId() == null) {
            entityManager.persist(loanApplication);
            entityManager.flush();

            return loanApplication;
        }
        entityManager.merge(loanApplication);
        entityManager.flush();
        return loanApplication;
    }

    /**
     * Query "berat": join ke Applicant, filter dinamis, DTO projection lewat
     * {@code selectNew(...)} (record biasa, TANPA annotation processor tambahan - beda
     * dari pola Entity View {@code @EntityView}), dan pagination.
     */
    public Page<LoanApplicationSearchResult> search(LoanApplicationSearchCriteria criteria, Pageable pageable) {
        CriteriaBuilder<LoanApplication> cb = criteriaBuilderFactory
                .create(entityManager, LoanApplication.class, "la")
                .leftJoin("la.applicant", "app");

        applyFilters(cb, criteria);
        applySort(cb, pageable.getSort());

        CriteriaBuilder<LoanApplicationSearchResult> resultCb = cb
                .selectNew(LoanApplicationSearchResult.class)
                .with("la.id")
                .with("app.fullName")
                .with("la.loanAmountRequested")
                .with("la.status")
                .with("la.submittedAt")
                .end();

        PagedList<LoanApplicationSearchResult> pagedList = resultCb
                .page(pageable.getPageNumber() * pageable.getPageSize(), pageable.getPageSize())
                .getResultList();

        return new PageImpl<>(pagedList, pageable, pagedList.getTotalSize());
    }

    private void applyFilters(CriteriaBuilder<LoanApplication> cb, LoanApplicationSearchCriteria criteria) {
        if (criteria.applicantName() != null && !criteria.applicantName().isBlank()) {
            cb.where("app.fullName").like(false).value("%" + criteria.applicantName() + "%").noEscape();
        }
        if (criteria.status() != null) {
            cb.where("la.status").eq(criteria.status());
        }
        if (criteria.submittedFrom() != null) {
            cb.where("la.submittedAt").ge(criteria.submittedFrom());
        }
        if (criteria.submittedTo() != null) {
            cb.where("la.submittedAt").le(criteria.submittedTo());
        }
        if (criteria.minAmount() != null) {
            cb.where("la.loanAmountRequested").ge(criteria.minAmount());
        }
        if (criteria.maxAmount() != null) {
            cb.where("la.loanAmountRequested").le(criteria.maxAmount());
        }
    }

    private void applySort(CriteriaBuilder<LoanApplication> cb, Sort sort) {
        if (sort.isUnsorted()) {
            cb.orderByDesc("la.submittedAt");
            return;
        }
        for (Sort.Order order : sort) {
            String path = switch (order.getProperty()) {
                case "submittedAt" -> "la.submittedAt";
                case "loanAmountRequested" -> "la.loanAmountRequested";
                case "status" -> "la.status";
                case "applicantFullName" -> "app.fullName";
                default -> throw new IllegalArgumentException(
                        "Sort property tidak didukung: " + order.getProperty());
            };
            cb.orderBy(path, order.isAscending());
        }
    }
}
