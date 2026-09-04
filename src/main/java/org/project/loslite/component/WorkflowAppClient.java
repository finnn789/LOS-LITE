package org.project.loslite.component;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.project.loslite.dto.WorkflowExecutionEventPayload;
import org.project.loslite.dto.WorkflowTaskInfo;
import org.project.loslite.dto.WorkflowTriggerResult;
import org.project.loslite.interfaces.WorkflowEngineClient;
import org.project.loslite.model.WorkflowLiteInstance;
import org.project.loslite.persist.WorkflowLiteInstancePersist;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Implementasi {@link WorkflowEngineClient} - panggil REST + WebSocket
 * {@code org.lite.project.workflow} ("WORKFLOW", lihat dokumentasi API-nya), BUKAN LAGI
 * WORKFLOW-APP/Camunda. WORKFLOW jauh lebih sederhana dari WORKFLOW-APP: satu definisi
 * BPMN linear (START_EVENT -> SERVICE_TASK -> END_EVENT, belum ada gateway), tidak kenal
 * businessKey/correlationId/topic sama sekali - cuma instanceId (UUID) & taskId (UUID)
 * miliknya sendiri.
 * <p>
 * businessKey -> instanceId WORKFLOW disimpan sendiri di sini lewat
 * {@link WorkflowLiteInstancePersist} ({@link WorkflowLiteInstance}), karena WORKFLOW
 * tidak punya tabel korelasi seperti itu. Task yang SEDANG terbuka (dipakai
 * {@link #findOpenTasks}/{@link #completeExternalTask}) di-cache di baris yang sama,
 * diisi oleh {@link #handleEvent} tiap kali {@link WorkflowSocketClient} terima event
 * WebSocket {@code NODE_STARTED}/{@code NODE_COMPLETED}/{@code NODE_FAILED}/
 * {@code WORKFLOW_COMPLETED}/{@code WORKFLOW_FAILED} dari topic
 * {@code /topic/workflow/{instanceId}} - {@link #startProcess} JUGA reconcile SEKALI
 * lewat REST ({@code GET .../tasks}) segera setelah start, karena WORKFLOW menjalankan
 * node pertama SECARA SINKRON di dalam request {@code POST .../start} itu sendiri (bisa
 * saja event WebSocket-nya sudah terkirim SEBELUM baris businessKey ini sempat
 * disimpan/di-subscribe - lihat {@link #reconcileOpenTask}).
 * <p>
 * Setiap method di sini BEST-EFFORT dan TIDAK PERNAH melempar exception ke pemanggil -
 * sama seperti kontrak {@link WorkflowEngineClient} & implementasi WORKFLOW-APP
 * sebelumnya yang digantikan class ini.
 */
@Component
public class WorkflowAppClient implements WorkflowEngineClient {

    private static final Logger log = LoggerFactory.getLogger(WorkflowAppClient.class);

    private final WebClient workflowAppWebClient;
    private final WorkflowSocketClient socketClient;
    private final WorkflowLiteInstancePersist instancePersist;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final String workflowKey;

    public WorkflowAppClient(
            WebClient workflowAppWebClient,
            WorkflowSocketClient socketClient,
            WorkflowLiteInstancePersist instancePersist,
            TransactionTemplate transactionTemplate,
            ObjectMapper objectMapper,
            @Value("${workflow-app.enabled}") boolean enabled,
            @Value("${workflow-app.workflow-key}") String workflowKey) {
        this.workflowAppWebClient = workflowAppWebClient;
        this.socketClient = socketClient;
        this.instancePersist = instancePersist;
        this.transactionTemplate = transactionTemplate;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.workflowKey = workflowKey;
    }

    @Override
    public WorkflowTriggerResult startProcess(String businessKey, String correlationId, Map<String, Object> variables) {
        if (!enabled) {
            return WorkflowTriggerResult.empty(businessKey, correlationId);
        }

        // Idempotency - WORKFLOW tidak punya correlationId/dedup sendiri (beda dari
        // WORKFLOW-APP yang balas 409 DUPLICATE_TRIGGER), jadi HARUS dicegah di sini:
        // start ulang tanpa guard ini akan bikin instance WORKFLOW baru tiap retry.
        Optional<WorkflowLiteInstance> existing = instancePersist.findByBusinessKey(businessKey);
        if (existing.isPresent()) {
            WorkflowLiteInstance mapped = existing.get();
            log.info("businessKey='{}' sudah pernah trigger instance WORKFLOW (instanceId={}) - kembalikan yang sudah ada, tidak start baru", businessKey, mapped.getInstanceId());
            return new WorkflowTriggerResult(mapped.getInstanceId(), correlationId, businessKey, mapped.getInstanceId(), mapped.getStatus(), Instant.now());
        }

        try {
            String inputData = objectMapper.writeValueAsString(variables);

            StartedInstanceView view = workflowAppWebClient.post()
                    .uri("/api/workflows/{workflowKey}/start", workflowKey)
                    .contentType(MediaType.TEXT_PLAIN)
                    .bodyValue(inputData)
                    .retrieve()
                    .bodyToMono(StartedInstanceView.class)
                    .block();

            if (view == null || view.instanceId() == null) {
                return WorkflowTriggerResult.empty(businessKey, correlationId);
            }

            WorkflowLiteInstance mapped = transactionTemplate.execute(status -> instancePersist.save(
                    WorkflowLiteInstance.builder()
                            .businessKey(businessKey)
                            .workflowKey(workflowKey)
                            .instanceId(view.instanceId())
                            .status(view.status())
                            .build()));

            reconcileOpenTask(mapped);

            socketClient.watch(view.instanceId(), event -> handleEvent(businessKey, event));

            return new WorkflowTriggerResult(view.instanceId(), correlationId, businessKey, view.instanceId(), view.status(), Instant.now());
        } catch (Exception e) {
            log.warn("Gagal trigger process WORKFLOW untuk businessKey={} - lanjut tanpa orkestrasi otomatis", businessKey, e);
            return WorkflowTriggerResult.empty(businessKey, correlationId);
        }
    }

    /**
     * Reconcile SEKALI segera setelah start - lihat javadoc class ini soal race dengan
     * event WebSocket node pertama. Best-effort murni: gagal di sini TIDAK fatal, event
     * WebSocket berikutnya (kalau ada task lanjutan) tetap akan mengisi
     * currentTaskId/currentNodeId seperti biasa lewat {@link #handleEvent}.
     */
    private void reconcileOpenTask(WorkflowLiteInstance mapped) {
        try {
            List<OpenTaskView> openTasks = workflowAppWebClient.get()
                    .uri("/api/workflows/instances/{instanceId}/tasks", mapped.getInstanceId())
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<OpenTaskView>>() {})
                    .block();

            if (openTasks == null || openTasks.isEmpty()) {
                return;
            }

            OpenTaskView task = openTasks.get(0);

            transactionTemplate.executeWithoutResult(status ->
                    instancePersist.findByBusinessKey(mapped.getBusinessKey()).ifPresent(fresh -> {
                        fresh.setCurrentTaskId(task.taskId());
                        fresh.setCurrentNodeId(task.nodeId());
                        instancePersist.save(fresh);
                    }));
        } catch (Exception e) {
            log.warn("Gagal reconcile open task awal untuk instanceId={} - andalkan event WebSocket berikutnya saja", mapped.getInstanceId(), e);
        }
    }

    @Override
    public List<WorkflowTaskInfo> findOpenTasks(String businessKey) {
        if (!enabled) {
            return List.of();
        }

        Optional<WorkflowLiteInstance> mapped = instancePersist.findByBusinessKey(businessKey);
        if (mapped.isEmpty() || mapped.get().getCurrentTaskId() == null) {
            return List.of();
        }

        WorkflowLiteInstance inst = mapped.get();
        return List.of(new WorkflowTaskInfo(inst.getCurrentTaskId(), inst.getCurrentNodeId(), inst.getInstanceId()));
    }

    @Override
    public void completeTask(String taskId, Map<String, Object> variables) {
        if (!enabled) {
            return;
        }

        try {
            String outputData = objectMapper.writeValueAsString(variables);

            workflowAppWebClient.post()
                    .uri("/api/workflows/tasks/{taskId}/complete", taskId)
                    .contentType(MediaType.TEXT_PLAIN)
                    .bodyValue(outputData)
                    .retrieve()
                    .toBodilessEntity()
                    .block();
        } catch (Exception e) {
            log.warn("Gagal complete task '{}' di WORKFLOW", taskId, e);
        }
    }

    @Override
    public void completeExternalTask(String businessKey, String topic, Map<String, Object> variables) {
        if (!enabled) {
            return;
        }

        // WORKFLOW tidak bedakan User Task vs External Task (topic) seperti Camunda -
        // semuanya SERVICE_TASK generik, selesai lewat endpoint /complete yang sama.
        // "topic" di sini diperlakukan sebagai nodeId BPMN yang harus SEDANG terbuka
        // untuk businessKey ini - kalau tidak match, itu state NORMAL (belum sampai
        // node itu / sudah pernah selesai), sama seperti kontrak 404 WORKFLOW-APP dulu.
        Optional<WorkflowLiteInstance> mapped = instancePersist.findByBusinessKey(businessKey);
        if (mapped.isEmpty() || mapped.get().getCurrentTaskId() == null || !topic.equals(mapped.get().getCurrentNodeId())) {
            log.info("Node '{}' tidak sedang terbuka untuk businessKey={} - state normal (belum sampai node itu / sudah pernah selesai / process belum start), skip", topic, businessKey);
            return;
        }

        completeTask(mapped.get().getCurrentTaskId(), variables);
    }

    /**
     * Dipanggil {@link WorkflowSocketClient} tiap kali event WebSocket masuk untuk
     * instance milik businessKey ini - jaga cache currentTaskId/currentNodeId/status di
     * {@link WorkflowLiteInstance} tetap sinkron. Berjalan di thread callback WebSocket
     * (BUKAN thread request HTTP), makanya {@link TransactionTemplate} dipakai di sini
     * juga (pola sama seperti alasan di javadoc LoanApplicationWorkflowService).
     */
    private void handleEvent(String businessKey, WorkflowExecutionEventPayload event) {
        transactionTemplate.executeWithoutResult(status ->
                instancePersist.findByBusinessKey(businessKey).ifPresent(inst -> {
                    switch (event.event()) {
                        case "NODE_STARTED" -> {
                            inst.setCurrentTaskId(event.taskId());
                            inst.setCurrentNodeId(event.nodeId());
                        }
                        case "NODE_COMPLETED", "NODE_FAILED" -> {
                            inst.setCurrentTaskId(null);
                            inst.setCurrentNodeId(null);
                        }
                        case "WORKFLOW_COMPLETED", "WORKFLOW_FAILED" -> {
                            inst.setStatus(event.status());
                            inst.setCurrentTaskId(null);
                            inst.setCurrentNodeId(null);
                        }
                        default -> log.warn("Event WORKFLOW tidak dikenal: '{}' (businessKey={})", event.event(), businessKey);
                    }
                    instancePersist.save(inst);
                }));
    }

    // ------------------------------------------------------------------
    // Bentuk wire JSON WORKFLOW - PRIVATE, tidak pernah bocor keluar class ini.
    // ------------------------------------------------------------------

    /**
     * Subset field response {@code POST /api/workflows/{workflowKey}/start} (entity
     * WorkflowInstance WORKFLOW) yang benar-benar dipakai di sini.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record StartedInstanceView(String instanceId, String status) {
    }

    /**
     * Subset field response {@code GET /api/workflows/instances/{id}/tasks}
     * (WorkflowTaskResponse WORKFLOW) yang dipakai {@link #reconcileOpenTask}.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OpenTaskView(String taskId, String nodeId, String status) {
    }
}
