package org.project.loslite.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Bentuk pesan yang dikirim WORKFLOW ke topic STOMP {@code /topic/workflow/{instanceId}}
 * (lihat {@code org.lite.project.workflow.dto.WorkflowExecutionEvent} di WORKFLOW - field
 * di sini WAJIB sama persis nama & urutan maknanya). {@code taskId}/{@code nodeId} null
 * untuk event level-instance ({@code WORKFLOW_COMPLETED}/{@code WORKFLOW_FAILED}); untuk
 * event level-task ({@code NODE_STARTED}/{@code NODE_COMPLETED}/{@code NODE_FAILED})
 * keduanya terisi. Dipakai oleh {@link org.project.loslite.component.WorkflowSocketClient}
 * & {@link org.project.loslite.component.WorkflowAppClient#handleEvent}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WorkflowExecutionEventPayload(
        String event,
        String workflowInstanceId,
        String taskId,
        String nodeId,
        String nodeType,
        String status
) {
}
