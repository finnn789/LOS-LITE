package org.project.loslite.domain.repository;

import org.project.loslite.domain.model.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    // Ambil semua histori perubahan untuk satu entity spesifik (polymorphic lookup).
    List<AuditLog> findByEntityTypeAndEntityId(String entityType, Long entityId);
}
