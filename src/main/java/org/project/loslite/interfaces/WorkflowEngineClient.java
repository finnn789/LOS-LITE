package org.project.loslite.interfaces;

import org.project.loslite.dto.WorkflowTaskInfo;
import org.project.loslite.dto.WorkflowTriggerResult;

import java.util.List;
import java.util.Map;

/**
 * Port ke workflow orchestrator eksternal (WORKFLOW-APP) - aplikasi Spring Boot terpisah
 * yang menjalankan proses BPMN lewat Camunda 7 (engine-rest) atas nama LOS-LITE. Detail
 * HTTP/JSON-nya (endpoint, envelope response, retry 409) sepenuhnya jadi urusan
 * implementasi ({@link org.project.loslite.component.WorkflowAppClient}) - pemakai port
 * ini (workflow/LoanApplicationWorkflowService) cuma tahu tiga operasi domain: mulai
 * proses, cari task terbuka, selesaikan task.
 */
public interface WorkflowEngineClient {

    /**
     * Memulai (atau, kalau correlationId sudah pernah dipakai, mengambil kembali) satu
     * business process trigger di WORKFLOW-APP. Implementasi WAJIB best-effort: gagal
     * hubungi WORKFLOW-APP tidak boleh melempar exception ke pemanggil - cukup kembalikan
     * hasil kosong/null-fields dan log warning, supaya domain LOS-LITE tetap jalan tanpa
     * orkestrasi.
     *
     * @param businessKey   correlation value milik LOS-LITE, dipakai WORKFLOW-APP sebagai
     *                      lookup key (lihat {@link #findOpenTasks})
     * @param correlationId idempotency key - HARUS sama persis kalau memanggil ulang untuk
     *                      business event yang sama (mis. retry HTTP di layer atas)
     * @param variables     process variable yang dikirim ke Camunda saat process instance start
     */
    WorkflowTriggerResult startProcess(String businessKey, String correlationId, Map<String, Object> variables);

    /**
     * Semua Camunda User Task yang sedang terbuka (state CREATED) untuk businessKey
     * tertentu, lintas semua trigger yang pernah tercatat. Kembalikan list kosong (BUKAN
     * exception) kalau WORKFLOW-APP tidak bisa dihubungi atau memang tidak ada task terbuka.
     */
    List<WorkflowTaskInfo> findOpenTasks(String businessKey);

    /**
     * Selesaikan satu User Task di WORKFLOW-APP. Best-effort sama seperti
     * {@link #startProcess} - implementasi menelan exception-nya sendiri (log warn), tidak
     * pernah melempar ke pemanggil.
     */
    void completeTask(String taskId, Map<String, Object> variables);

    /**
     * Selesaikan satu Camunda EXTERNAL TASK (BUKAN User Task, lihat {@link #completeTask})
     * untuk businessKey & topic tertentu - dipakai node BPMN yang
     * {@code camunda:type="external"} (mis. topic {@code "document-verification"}).
     * Implementasi WAJIB fetch+lock+complete-nya SECARA SINKRON dalam satu request ke
     * WORKFLOW-APP (bukan lewat mekanisme fetchAndLock polling worker terpisah) - lihat
     * dokumentasi kontrak endpoint
     * {@code POST /api/v1/business-processes/{businessKey}/external-tasks/{topic}/complete}.
     * <p>
     * Best-effort sama seperti {@link #completeTask} - implementasi menelan exception-nya
     * sendiri, tidak pernah melempar ke pemanggil. 404 (task belum sampai node itu / sudah
     * pernah di-complete sebelumnya / process belum start - dokumentasi kontrak endpoint
     * ini mengembalikan errorCode yang SAMA untuk semua kasus tsb) adalah state NORMAL;
     * error selain itu (mis. 502 - Camunda menolak variable atau engine unreachable)
     * menandakan kemungkinan bug integrasi nyata dan harus dibedakan level log-nya oleh
     * implementasi.
     */
    void completeExternalTask(String businessKey, String topic, Map<String, Object> variables);
}
