package org.project.loslite.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Satu Camunda User Task yang sedang terbuka di WORKFLOW-APP (endpoint GET
 * /api/v1/business-processes/{businessKey}/tasks, field "data[]" - lihat TaskResponse di
 * dokumentasi WORKFLOW-APP). Cuma field yang benar-benar dipakai LOS-LITE
 * (LoanApplicationWorkflowService mencari task lewat taskDefinitionKey, complete lewat
 * id) yang di-map di sini - name/assignee/created/formKey/processDefinitionId dari
 * response asli sengaja tidak ikut ditarik.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WorkflowTaskInfo(
        String id,
        String taskDefinitionKey,
        String processInstanceId
) {
}
