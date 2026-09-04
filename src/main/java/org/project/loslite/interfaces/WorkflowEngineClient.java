package org.project.loslite.interfaces;

import org.project.loslite.dto.WorkflowTaskInfo;
import org.project.loslite.dto.WorkflowTriggerResult;

import java.util.List;
import java.util.Map;

/**
 * Port ke workflow engine eksternal - aplikasi Spring Boot terpisah, {@code WORKFLOW}
 * ({@code org.lite.project.workflow}, BUKAN LAGI WORKFLOW-APP/Camunda 7, lihat histori
 * git untuk implementasi lama) yang menjalankan proses BPMN atas nama LOS-LITE. Detail
 * HTTP/JSON DAN WebSocket-nya (endpoint, korelasi businessKey -> instanceId WORKFLOW,
 * live event) sepenuhnya jadi urusan implementasi
 * ({@link org.project.loslite.component.WorkflowAppClient}, dibantu
 * {@link org.project.loslite.component.WorkflowSocketClient}) - pemakai port ini
 * (workflow/LoanApplicationWorkflowService) cuma tahu tiga operasi domain: mulai proses,
 * cari task terbuka, selesaikan task.
 * <p>
 * CATATAN: WORKFLOW saat ini belum punya gateway/conditional routing (cuma jalur linear
 * START_EVENT -> SERVICE_TASK -> END_EVENT) - proses "loan-application-process" asli
 * (dengan {@code Gateway_ScoringDecision}) BELUM bisa dijalankan WORKFLOW apa adanya,
 * lihat {@link org.project.loslite.workflow.LoanApplicationWorkflowService}. Sampai
 * WORKFLOW dapat dukungan gateway, wiring ini jalan di atas definisi BPMN linear
 * pengganti (lihat config {@code workflow-app.workflow-key}).
 */
public interface WorkflowEngineClient {

    /**
     * Memulai (atau, kalau businessKey sudah pernah dipakai, mengambil kembali) satu
     * workflow instance di WORKFLOW. Implementasi WAJIB best-effort: gagal hubungi
     * WORKFLOW tidak boleh melempar exception ke pemanggil - cukup kembalikan hasil
     * kosong/null-fields dan log warning, supaya domain LOS-LITE tetap jalan tanpa
     * orkestrasi.
     *
     * @param businessKey   correlation value milik LOS-LITE - WORKFLOW sendiri tidak
     *                      kenal konsep ini, jadi implementasi yang menyimpan pemetaan
     *                      businessKey -> instanceId WORKFLOW sendiri (lihat
     *                      {@link #findOpenTasks})
     * @param correlationId idempotency key - HARUS sama persis kalau memanggil ulang untuk
     *                      business event yang sama (mis. retry HTTP di layer atas)
     * @param variables     process variable yang dikirim (sebagai inputData) saat workflow
     *                      instance start
     */
    WorkflowTriggerResult startProcess(String businessKey, String correlationId, Map<String, Object> variables);

    /**
     * Semua task WORKFLOW yang sedang terbuka (belum di-complete/fail) untuk businessKey
     * tertentu. WORKFLOW sendiri single-jalur (belum ada gateway paralel), jadi PALING
     * BANYAK satu task terbuka per instance. Kembalikan list kosong (BUKAN exception)
     * kalau WORKFLOW tidak bisa dihubungi atau memang tidak ada task terbuka.
     */
    List<WorkflowTaskInfo> findOpenTasks(String businessKey);

    /**
     * Selesaikan satu task WORKFLOW. Best-effort sama seperti {@link #startProcess} -
     * implementasi menelan exception-nya sendiri (log warn), tidak pernah melempar ke
     * pemanggil.
     */
    void completeTask(String taskId, Map<String, Object> variables);

    /**
     * Selesaikan task WORKFLOW yang nodeId-nya cocok dengan {@code topic}, untuk
     * businessKey tertentu - nama method & parameter {@code topic} dipertahankan dari
     * kontrak Camunda EXTERNAL TASK lama (WORKFLOW sendiri tidak bedakan User Task vs
     * External Task, semua SERVICE_TASK generik) supaya
     * {@code LoanApplicationWorkflowService} tidak perlu berubah.
     * <p>
     * Best-effort sama seperti {@link #completeTask} - implementasi menelan exception-nya
     * sendiri, tidak pernah melempar ke pemanggil. "Task node ini belum/tidak sedang
     * terbuka" (belum sampai node itu / sudah pernah di-complete sebelumnya / process
     * belum start) adalah state NORMAL, di-skip diam-diam (log info); error lain (mis.
     * WORKFLOW unreachable) menandakan kemungkinan bug integrasi nyata dan harus
     * dibedakan level log-nya oleh implementasi.
     */
    void completeExternalTask(String businessKey, String topic, Map<String, Object> variables);
}
