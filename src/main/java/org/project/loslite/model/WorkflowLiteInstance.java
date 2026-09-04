package org.project.loslite.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Satu baris = satu businessKey yang pernah trigger process di WORKFLOW (lihat
 * component/WorkflowAppClient). WORKFLOW sendiri tidak kenal konsep businessKey (cuma
 * instanceId internal, UUID acak) - tabel ini yang jadi "kamus" businessKey -> instanceId
 * di sisi LOS-LITE, sekaligus cache status instance & task yang SEDANG terbuka
 * (currentTaskId/currentNodeId), diisi oleh WorkflowSocketClient tiap kali event
 * WebSocket NODE_STARTED/NODE_COMPLETED/dst masuk - lihat WorkflowAppClient#handleEvent.
 * <p>
 * WORKFLOW cuma pernah punya PALING BANYAK SATU task terbuka per instance (engine-nya
 * jalan satu jalur lurus, tidak ada gateway paralel) - makanya cukup 1 kolom
 * currentTaskId/currentNodeId, bukan collection.
 */
@Entity
@Table(
        name = "workflow_lite_instances",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_workflow_lite_instance_business_key", columnNames = "business_key"),
                @UniqueConstraint(name = "uk_workflow_lite_instance_instance_id", columnNames = "instance_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class WorkflowLiteInstance extends BaseEntity {

    @Column(name = "business_key", nullable = false, length = 100)
    private String businessKey;

    @Column(name = "workflow_key", nullable = false, length = 100)
    private String workflowKey;

    @Column(name = "instance_id", nullable = false, length = 100)
    private String instanceId;

    // Status instance WORKFLOW terakhir yang diketahui (RUNNING/COMPLETED/FAILED/
    // CANCELLED) - opaque string, sama seperti LoanApplication#workflowStatus.
    @Column(name = "status", length = 30)
    private String status;

    // Task yang SEDANG terbuka untuk instance ini, null kalau tidak ada (baru selesai,
    // sudah completed, atau workflow sudah tuntas) - diisi/dikosongkan oleh
    // WorkflowAppClient#handleEvent tiap event WebSocket masuk.
    @Column(name = "current_task_id", length = 100)
    private String currentTaskId;

    @Column(name = "current_node_id", length = 100)
    private String currentNodeId;
}
