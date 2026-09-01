package org.project.loslite.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

/**
 * Hasil trigger business process di WORKFLOW-APP (endpoint POST
 * /api/v1/triggers/business-process, field "data" - lihat TriggerAcceptedResponse di
 * dokumentasi WORKFLOW-APP). status "STARTED" = camundaProcessInstanceId terisi;
 * "FAILED" = trigger tercatat tapi Camunda gagal dipanggil - WORKFLOW-APP TIDAK retry
 * otomatis (tidak ada lagi ProcessReconciliationService di WORKFLOW-APP, sudah dihapus),
 * jadi trigger yang FAILED akan diam sampai ada yang retry manual lewat WORKFLOW-APP
 * (POST /api/v1/business-processes/id/{triggerId}/retry) - LOS-LITE saat ini tidak punya
 * mekanisme otomatis untuk itu, lihat {@link org.project.loslite.workflow.LoanApplicationWorkflowService#startProcess}.
 * <p>
 * Semua field nullable dari sisi LOS-LITE: kalau WORKFLOW-APP sama sekali tidak bisa
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
