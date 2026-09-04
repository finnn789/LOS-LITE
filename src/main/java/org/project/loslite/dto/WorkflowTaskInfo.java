package org.project.loslite.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Satu task WORKFLOW yang sedang terbuka untuk businessKey tertentu - dibangun oleh
 * {@link org.project.loslite.component.WorkflowAppClient#findOpenTasks} dari cache
 * currentTaskId/currentNodeId di {@link org.project.loslite.model.WorkflowLiteInstance}
 * (BUKAN hasil parsing langsung response WORKFLOW - engine itu tidak punya endpoint
 * "list task per businessKey", cuma per instanceId). {@code taskDefinitionKey} diisi
 * nodeId BPMN (nama field dipertahankan dari kontrak Camunda lama - LoanApplicationWorkflowService
 * mencari task lewat field ini, complete lewat {@code id}); {@code processInstanceId}
 * diisi instanceId WORKFLOW.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WorkflowTaskInfo(
        String id,
        String taskDefinitionKey,
        String processInstanceId
) {
}
