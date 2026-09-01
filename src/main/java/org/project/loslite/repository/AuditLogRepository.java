package org.project.loslite.repository;

import com.blazebit.persistence.CriteriaBuilder;
import com.blazebit.persistence.CriteriaBuilderFactory;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.project.loslite.model.AuditLog;
import org.project.loslite.model.QAuditLog;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Satu-satunya kelas akses data {@link AuditLog}. SENGAJA class biasa, BUKAN interface
 * {@code extends JpaRepository} - lihat catatan pola yang sama di {@code ApplicantRepository}:
 * QueryDSL ({@link QAuditLog}) cuma sebagai generator nama
 * path type-safe, mesin eksekusi query tetap Blaze-Persistence {@link CriteriaBuilder}.
 * <p>
 * Method di sini sengaja tidak {@code @Transactional} - transaksi jadi tanggung jawab
 * Service pemanggil ({@code LoanApplicationStatusService}).
 */
@Repository
@RequiredArgsConstructor
public class AuditLogRepository {

    private static final QAuditLog qAuditLog = new QAuditLog("al");

    private final CriteriaBuilderFactory criteriaBuilderFactory;
    private final EntityManager entityManager;

    // Ambil semua histori perubahan untuk satu entity spesifik (polymorphic lookup).
    public List<AuditLog> findByEntityTypeAndEntityId(String entityType, Long entityId) {
        CriteriaBuilder<AuditLog> cb = criteriaBuilderFactory
                .create(entityManager, AuditLog.class, "al")
                .where(qAuditLog.entityType.toString()).eq(entityType)
                .where(qAuditLog.entityId.toString()).eq(entityId)
                .orderByAsc(qAuditLog.id.toString());

        return cb.getResultList();
    }

    /**
     * Insert kalau {@code auditLog.getId() == null}, update (merge) kalau sudah ada id.
     * Harus dipanggil dalam konteks transaksi ({@code @Transactional} di Service pemanggil).
     */
    public AuditLog save(AuditLog auditLog) {
        if (auditLog.getId() == null) {
            entityManager.persist(auditLog);
            return auditLog;
        }
        return entityManager.merge(auditLog);
    }
}
