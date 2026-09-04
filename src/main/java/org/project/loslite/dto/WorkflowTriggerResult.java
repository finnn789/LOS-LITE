package org.project.loslite.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

/**
 * Hasil start workflow instance di WORKFLOW (endpoint POST
 * /api/workflows/{workflowKey}/start). {@code triggerId} & {@code camundaProcessInstanceId}
 * (nama field dipertahankan dari kontrak WORKFLOW-APP/Camunda lama supaya
 * {@code LoanApplicationWorkflowService} tidak perlu berubah) SAMA-SAMA diisi instanceId
 * WORKFLOW - engine ini tidak punya konsep triggerId/processInstanceId terpisah seperti
 * Camunda dulu. {@code status} nilainya langsung dari
 * {@code WorkflowInstanceStatus} WORKFLOW (RUNNING/COMPLETED/FAILED/CANCELLED).
 * <p>
 * Semua field nullable dari sisi LOS-LITE: kalau WORKFLOW sama sekali tidak bisa
 * dihubungi, {@link org.project.loslite.component.WorkflowAppClient} mengembalikan
 * instance dengan semua field null alih-alih melempar exception (lihat javadoc
 * {@link org.project.loslite.interfaces.WorkflowEngineClient#startProcess}).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WorkflowTriggerResult(
        String triggerId,
        String correlationId,
        String businessKey,
        String camundaProcessInstanceId,
        String status,
        Instant acceptedAt
) {
    public static WorkflowTriggerResult empty(String businessKey, String correlationId) {
        return new WorkflowTriggerResult(null, correlationId, businessKey, null, null, null);
    }
}
