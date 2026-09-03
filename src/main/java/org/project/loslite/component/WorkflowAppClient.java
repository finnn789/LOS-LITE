package org.project.loslite.component;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.project.loslite.dto.WorkflowTaskInfo;
import org.project.loslite.dto.WorkflowTriggerResult;
import org.project.loslite.interfaces.WorkflowEngineClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Implementasi ASLI dari port {@link WorkflowEngineClient} - panggil REST API WORKFLOW-APP
 * (aplikasi terpisah, lihat dokumentasi integrasinya) lewat WebClient SECARA BLOCKING,
 * pola blocking-call di atas WebClient yang sebelumnya juga dipakai integrasi OCR
 * (sudah dicabut). Detail teknis (envelope
 * {@code ApiResponse<T>} WORKFLOW-APP, retry 409 DUPLICATE_TRIGGER, resiliency) hidup
 * SEPENUHNYA di sini - workflow/LoanApplicationWorkflowService tidak tahu-menahu ini HTTP.
 * <p>
 * Setiap method di sini BEST-EFFORT dan TIDAK PERNAH melempar exception ke pemanggil -
 * WORKFLOW-APP mati/unreachable tidak boleh bikin domain LOS-LITE (perubahan status via
 * LoanApplicationStatusService) gagal, sama seperti pola dual-mode CamundaClient yang
 * digantikan class ini (lihat komentar di application.yml, workflow-app.enabled).
 */
@Component
public class WorkflowAppClient implements WorkflowEngineClient {

    private static final Logger log = LoggerFactory.getLogger(WorkflowAppClient.class);

    private final WebClient workflowAppWebClient;
    private final boolean enabled;
    private final String serviceCode;
    private final String businessFlowCode;

    public WorkflowAppClient(
            WebClient workflowAppWebClient,
            @Value("${workflow-app.enabled}") boolean enabled,
            @Value("${workflow-app.service-code}") String serviceCode,
            @Value("${workflow-app.business-flow-code}") String businessFlowCode) {
        this.workflowAppWebClient = workflowAppWebClient;
        this.enabled = enabled;
        this.serviceCode = serviceCode;
        this.businessFlowCode = businessFlowCode;
    }

    @Override
    public WorkflowTriggerResult startProcess(String businessKey, String correlationId, Map<String, Object> variables) {
        if (!enabled) {
            return WorkflowTriggerResult.empty(businessKey, correlationId);
        }

        TriggerRequest body = new TriggerRequest(serviceCode, businessFlowCode, businessKey, correlationId, variables);

        try {
            ApiEnvelope<WorkflowTriggerResult> envelope = workflowAppWebClient.post()
                    .uri("/api/v1/triggers/business-process")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<ApiEnvelope<WorkflowTriggerResult>>() {})
                    .block();

            return envelope != null ? envelope.data() : WorkflowTriggerResult.empty(businessKey, correlationId);
        } catch (WebClientResponseException.Conflict e) {
            // 409 DUPLICATE_TRIGGER - correlationId sudah pernah dipakai (retry HTTP di
            // layer atas, mis. endpoint /submit dipanggil ulang). Dokumentasi WORKFLOW-APP
            // eksplisit: ini "already accepted, no action needed", BUKAN error - ambil
            // trigger yang sudah ada lewat businessKey alih-alih menganggap gagal.
            log.info("correlationId '{}' sudah pernah dipakai (409 DUPLICATE_TRIGGER) - ambil trigger existing untuk businessKey={}", correlationId, businessKey);
            return fetchExistingTrigger(businessKey, correlationId);
        } catch (Exception e) {
            log.warn("Gagal trigger business process di WORKFLOW-APP untuk businessKey={} - lanjut tanpa orkestrasi otomatis", businessKey, e);
            return WorkflowTriggerResult.empty(businessKey, correlationId);
        }
    }

    private WorkflowTriggerResult fetchExistingTrigger(String businessKey, String correlationId) {
        try {
            ApiEnvelope<TriggerDetailView> envelope = workflowAppWebClient.get()
                    .uri("/api/v1/business-processes/{businessKey}", businessKey)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<ApiEnvelope<TriggerDetailView>>() {})
                    .block();

            TriggerDetailView view = envelope != null ? envelope.data() : null;
            if (view == null) {
                return WorkflowTriggerResult.empty(businessKey, correlationId);
            }

            return new WorkflowTriggerResult(view.id(), view.correlationId(), view.businessKey(), view.camundaProcessInstanceId(), view.status(), null);
        } catch (Exception e) {
            log.warn("Gagal ambil trigger existing untuk businessKey={} setelah 409 DUPLICATE_TRIGGER", businessKey, e);
            return WorkflowTriggerResult.empty(businessKey, correlationId);
        }
    }

    @Override
    public List<WorkflowTaskInfo> findOpenTasks(String businessKey) {
        if (!enabled) {
            return List.of();
        }

        try {
            ApiEnvelope<List<WorkflowTaskInfo>> envelope = workflowAppWebClient.get()
                    .uri("/api/v1/business-processes/{businessKey}/tasks", businessKey)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<ApiEnvelope<List<WorkflowTaskInfo>>>() {})
                    .block();

            return envelope != null && envelope.data() != null ? envelope.data() : List.of();
        } catch (Exception e) {
            log.warn("Gagal ambil open tasks WORKFLOW-APP untuk businessKey={}", businessKey, e);
            return List.of();
        }
    }

    @Override
    public void completeTask(String taskId, Map<String, Object> variables) {
        if (!enabled) {
            return;
        }

        try {
            workflowAppWebClient.post()
                    .uri("/api/v1/tasks/{taskId}/complete", taskId)
                    .bodyValue(new CompleteTaskRequest(variables))
                    .retrieve()
                    .toBodilessEntity()
                    .block();
        } catch (Exception e) {
            log.warn("Gagal complete task '{}' di WORKFLOW-APP", taskId, e);
        }
    }

    @Override
    public void completeExternalTask(String businessKey, String topic, Map<String, Object> variables) {
        if (!enabled) {
            return;
        }

        try {
            ApiEnvelope<ExternalTaskCompletionView> envelope = workflowAppWebClient.post()
                    .uri("/api/v1/business-processes/{businessKey}/external-tasks/{topic}/complete", businessKey, topic)
                    .bodyValue(new CompleteTaskRequest(variables))
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<ApiEnvelope<ExternalTaskCompletionView>>() {})
                    .block();

            ExternalTaskCompletionView data = envelope != null ? envelope.data() : null;
            log.info("External task topic='{}' berhasil di-complete di WORKFLOW-APP untuk businessKey={} (externalTaskId={}, activityId={})",
                    topic, businessKey, data != null ? data.externalTaskId() : null, data != null ? data.activityId() : null);
        } catch (WebClientResponseException.NotFound e) {
            // 404 RESOURCE_NOT_FOUND - dokumentasi kontrak endpoint ini eksplisit: SEMUA
            // kasus "tidak ketemu" (businessKey belum pernah trigger, process Camunda
            // belum start, task belum sampai node itu, ATAU sudah pernah di-complete
            // sebelumnya) dikembalikan errorCode yang SAMA, cuma beda di `message` bebas -
            // sengaja TIDAK dibedakan lagi di sini. Diperlakukan sebagai state NORMAL
            // (mis. retry aman kalau endpoint document-verification LOS-LITE dipanggil
            // dua kali), bukan kegagalan - makanya info, bukan warn.
            log.info("External task topic='{}' tidak ditemukan untuk businessKey={} - state normal (belum sampai node itu / sudah pernah selesai / process belum start), skip", topic, businessKey);
        } catch (Exception e) {
            // Termasuk 502 CAMUNDA_INTEGRATION_ERROR - BEDA KELAS masalah dari 404 di
            // atas: variable ditolak Camunda atau engine unreachable, kemungkinan BUG
            // INTEGRASI NYATA (mis. gateway BPMN berikutnya butuh variable yang tidak kita
            // kirim), bukan sekadar "belum waktunya". Tetap best-effort (tidak throw ke
            // pemanggil - lihat javadoc WorkflowEngineClient#completeExternalTask), tapi
            // log lebih keras supaya tidak kelewat kalau memang ada masalah.
            log.warn("Gagal complete external task topic='{}' di WORKFLOW-APP untuk businessKey={} - kemungkinan BUG INTEGRASI (variable ditolak Camunda / engine unreachable), BUKAN state normal seperti 404", topic, businessKey, e);
        }
    }

    // ------------------------------------------------------------------
    // Bentuk wire JSON WORKFLOW-APP - PRIVATE, tidak pernah bocor keluar class ini.
    // ------------------------------------------------------------------

    private record TriggerRequest(
            String serviceCode,
            String businessFlowCode,
            String businessKey,
            String correlationId,
            Map<String, Object> variables) {
    }

    private record CompleteTaskRequest(Map<String, Object> variables) {
    }

    /**
     * Amplop response sukses WORKFLOW-APP: {@code {success, message, data, timestamp,
     * errorCode}} - BEDA bentuk dari {@link org.project.loslite.dto.ApiResponse} milik
     * LOS-LITE sendiri (tidak punya errorCode, tapi punya validationErrors), makanya
     * envelope terpisah di sini, bukan reuse.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ApiEnvelope<T>(boolean success, String message, T data, Instant timestamp, String errorCode) {
    }

    /**
     * Subset field BusinessProcessTriggerDetailView yang dipakai - field id di response
     * WORKFLOW-APP ini yang menjadi triggerId (bukan "triggerId", beda nama dari
     * TriggerAcceptedResponse hasil endpoint start).
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TriggerDetailView(
            String id,
            String correlationId,
            String businessKey,
            String camundaProcessInstanceId,
            String status) {
    }

    /**
     * Subset field response sukses {@code POST .../external-tasks/{topic}/complete} -
     * lihat dokumentasi kontrak endpoint ini di WORKFLOW-APP. Cuma dipakai untuk logging
     * ({@link #completeExternalTask}) - {@link WorkflowEngineClient#completeExternalTask}
     * sendiri best-effort/void, jadi tidak ada yang perlu ditulis balik ke database.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ExternalTaskCompletionView(
            String externalTaskId,
            String topicName,
            String processInstanceId,
            String activityId) {
    }
}
