package org.project.loslite.persist;

import com.blazebit.persistence.CriteriaBuilderFactory;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.project.loslite.model.WorkflowLiteInstance;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Akses data {@link WorkflowLiteInstance} - kamus businessKey -> instanceId WORKFLOW.
 * Pola sama persis dengan {@link LoanApplicationPersist}: class biasa di atas
 * EntityManager + Blaze-Persistence CriteriaBuilder, BUKAN {@code JpaRepository}. Method
 * di sini sengaja tidak {@code @Transactional} - transaksi jadi tanggung jawab
 * pemanggil (lihat {@code TransactionTemplate} di WorkflowAppClient, termasuk saat
 * dipanggil dari thread callback WebSocket, bukan thread request HTTP).
 */
@Repository
@RequiredArgsConstructor
public class WorkflowLiteInstancePersist {

    private final EntityManager entityManager;
    private final CriteriaBuilderFactory criteriaBuilderFactory;

    public Optional<WorkflowLiteInstance> findByBusinessKey(String businessKey) {
        return criteriaBuilderFactory
                .create(entityManager, WorkflowLiteInstance.class, "wli")
                .where("wli.businessKey").eq(businessKey)
                .getResultList()
                .stream()
                .findFirst();
    }

    public WorkflowLiteInstance save(WorkflowLiteInstance instance) {
        if (instance.getId() == null) {
            entityManager.persist(instance);
            entityManager.flush();
            return instance;
        }
        entityManager.merge(instance);
        entityManager.flush();
        return instance;
    }
}
