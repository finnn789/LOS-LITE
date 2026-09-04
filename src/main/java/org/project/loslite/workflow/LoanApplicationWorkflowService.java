package org.project.loslite.workflow;

import lombok.RequiredArgsConstructor;
import org.project.loslite.dto.CreateLoanApplicationCommand;
import org.project.loslite.dto.WorkflowTaskInfo;
import org.project.loslite.dto.WorkflowTriggerResult;
import org.project.loslite.enums.LoanStatus;
import org.project.loslite.enums.ReviewDecision;
import org.project.loslite.enums.ScoringDecision;
import org.project.loslite.interfaces.WorkflowEngineClient;
import org.project.loslite.model.LoanApplication;
import org.project.loslite.persist.LoanApplicationPersist;
import org.project.loslite.service.LoanApplicationService;
import org.project.loslite.service.LoanApplicationStatusService;
import org.project.loslite.service.ScoringOutcome;
import org.project.loslite.service.ScoringService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Orkestrator untuk satu {@link LoanApplication} yang terikat ke WORKFLOW (lihat
 * {@link WorkflowEngineClient}) - satu-satunya tempat yang tahu detail integrasi workflow
 * engine (trigger process, cari & complete task). {@link LoanApplicationService} sendiri
 * tidak pernah tahu WORKFLOW ada.
 * <p>
 * {@link WorkflowEngineClient} best-effort sepenuhnya di sisi implementasinya - broker
 * mati/unreachable TIDAK PERNAH bikin request di sini gagal: perubahan status di database
 * (lewat {@link LoanApplicationStatusService}) tetap sah duluan, orkestrasi WORKFLOW cuma
 * "mengikuti" secara best-effort.
 * <p>
 * <strong>Batas transaksi - baca ini sebelum ubah {@link #submit}/{@link #review}/
 * {@link #disburse}:</strong> ketiganya TIDAK dianotasi {@code @Transactional}.
 * {@link WorkflowEngineClient} manggil WORKFLOW lewat WebClient SECARA BLOCKING
 * ({@code .block()}) - kalau method ini dianotasi {@code @Transactional}, koneksi JDBC dari
 * pool akan tersandera selama seluruh durasi HTTP call itu. Karena itu tiap use-case
 * dipecah 2 fase eksplisit lewat {@link TransactionTemplate} (bukan {@code @Transactional},
 * karena {@link #startProcess}/{@link #completeUserTask} dipanggil dari method publik di
 * class yang sama - self-invocation membuat proxy {@code @Transactional} Spring diam-diam
 * tidak berlaku):
 * <ol>
 *     <li><strong>Fase status lokal</strong> (transaksi pendek, commit duluan): ubah status
 *     {@link LoanApplication}, simpan. Ini jadi kebenaran domain LOS-LITE.</li>
 *     <li><strong>Fase panggil WORKFLOW</strong> (tanpa transaksi terbuka): panggil
 *     {@link WorkflowEngineClient}.</li>
 * </ol>
 * <p>
 * {@code TASK_DEFINITION_*}/{@code EXTERNAL_TASK_TOPIC_*} di bawah HARUS SAMA PERSIS dengan
 * {@code id} node BPMN yang di-deploy ke WORKFLOW. Kalau BPMN-nya di-redeploy dengan id
 * yang beda, konstanta ini WAJIB disesuaikan ulang - tidak ada validasi otomatis yang
 * mendeteksi mismatch ini selain gejala tidak langsung: task/gateway terkait tidak pernah
 * ke-complete/ke-route, cuma kelihatan dari log warn {@link #completeUserTask}/
 * {@link #completeExternalTask}.
 */
@Component
@RequiredArgsConstructor

public class LoanApplicationWorkflowService {

    private static final Logger log = LoggerFactory.getLogger(LoanApplicationWorkflowService.class);

    // id node BPMN "Process_1" (2 exclusiveGateway: Gateway_ScoringDecision & gateway_review)
    // yang dideploy ke WORKFLOW - HARUS SAMA PERSIS dengan id di file .bpmn yang di-deploy,
    // lihat javadoc class ini.
    private static final String TASK_DEFINITION_OFFICER_REVIEW = "officer_review";
    private static final String TASK_DEFINITION_DISBURSE_FUNDS = "disburse_task";
    private static final String EXTERNAL_TASK_TOPIC_DOCUMENT_VERIFICATION = "verifikasi_dokumen";
    private static final String EXTERNAL_TASK_TOPIC_RUN_SCORING = "jalankan_scoring";

    private final LoanApplicationService loanApplicationService;
    private final ScoringService scoringService;
    private final LoanApplicationPersist loanApplicationPersist;
    private final LoanApplicationStatusService loanApplicationStatusService;
    private final WorkflowEngineClient workflowEngineClient;
    private final TransactionTemplate transactionTemplate;


    public LoanApplication submit(Long loanApplicationId) {
        LoanApplication saved = transactionTemplate.execute(status -> {
            LoanApplication loanApplication = loanApplicationService.getById(loanApplicationId);
            loanApplicationStatusService.changeStatus(loanApplication, LoanStatus.SUBMITTED, null);
            loanApplication.setSubmittedAt(Instant.now());
            return loanApplicationPersist.save(loanApplication);
        });

        // Di luar transaksi manapun - lihat Javadoc class ini soal kenapa HTTP call ke
        // WORKFLOW tidak boleh menyandera koneksi JDBC yang sedang terbuka.
        startProcess(saved);

        return saved;
    }


    /**
     * Tandai verifikasi dokumen selesai. Fase 1 REUSE
     * {@link LoanApplicationService#moveToDocumentVerification} apa adanya (cross-bean
     * call, jadi proxy {@code @Transactional} beneran berlaku dan commit duluan sebelum
     * Fase 2 di bawah jalan).
     */
    public LoanApplication moveToDocumentVerification(Long loanApplicationId) {
        LoanApplication saved = loanApplicationService.moveToDocumentVerification(loanApplicationId);

        completeExternalTask(saved, EXTERNAL_TASK_TOPIC_DOCUMENT_VERIFICATION, Map.of());

        return saved;
    }

    /**
     * Jalankan scoring, lalu selesaikan node {@code "run-scoring"} di WORKFLOW - kirim
     * variable {@code decision} (nilai {@link ScoringDecision}) supaya gateway keputusan
     * scoring di BPMN bisa route. Fase 1 REUSE {@link ScoringService#score} apa adanya
     * (pola sama seperti {@link #moveToDocumentVerification}). {@code loanApplication}
     * di-load ULANG setelah Fase 1 karena {@link ScoringOutcome} tidak membawa entity-nya.
     */
    public ScoringOutcome runScoring(Long loanApplicationId) {
        ScoringOutcome outcome = scoringService.score(loanApplicationId);

        LoanApplication loanApplication = loanApplicationService.getById(loanApplicationId);
        completeExternalTask(loanApplication, EXTERNAL_TASK_TOPIC_RUN_SCORING, Map.of("decision", outcome.decision().name()));

        return outcome;
    }

    /**
     * Dipanggil staff (officer/admin) untuk menutup jalur MANUAL_REVIEW - satu-satunya
     * cara pengajuan yang "menggantung" di status SCORING (lihat
     * ScoringService#applyDecisionToLoanStatus, case MANUAL_REVIEW) bisa lanjut ke
     * APPROVED/REJECTED.
     */
    public LoanApplication review(Long loanApplicationId, ReviewDecision decision) {
        LoanApplication saved = transactionTemplate.execute(status -> {
            LoanApplication loanApplication = loanApplicationService.getById(loanApplicationId);
            LoanStatus newStatus = decision == ReviewDecision.APPROVE ? LoanStatus.APPROVED : LoanStatus.REJECTED;
            loanApplicationStatusService.changeStatus(loanApplication, newStatus, null);
            loanApplication.setDecidedAt(Instant.now());
            return loanApplicationPersist.save(loanApplication);
        });

        completeUserTask(saved, TASK_DEFINITION_OFFICER_REVIEW, Map.of("reviewDecision", decision.name()));

        return saved;
    }

    /**
     * Dipanggil staff untuk mencairkan dana pengajuan yang sudah APPROVED.
     */
    public LoanApplication disburse(Long loanApplicationId) {
        LoanApplication saved = transactionTemplate.execute(status -> {
            LoanApplication loanApplication = loanApplicationService.getById(loanApplicationId);
            loanApplicationStatusService.changeStatus(loanApplication, LoanStatus.DISBURSED, null);
            return loanApplicationPersist.save(loanApplication);
        });

        completeUserTask(saved, TASK_DEFINITION_DISBURSE_FUNDS, Map.of());

        return saved;
    }

    /**
     * businessKey yang dikirim ke WORKFLOW - deterministik dari id, TIDAK disimpan sebagai
     * kolom terpisah.
     */
    private String businessKeyOf(Long loanApplicationId) {
        return "LOAN-" + loanApplicationId;
    }

    /**
     * Fase 2 dari {@link #submit}: panggil WORKFLOW (tanpa transaksi terbuka), lalu tulis
     * balik hasilnya ({@code triggerId}, {@code camundaProcessInstanceId}, {@code status})
     * dalam transaksi pendek terpisah. {@code loanApplication} di-load ulang di sini supaya
     * entity yang di-{@code save} attached ke transaksi baru ini, bukan instance detached
     * dari transaksi {@link #submit} yang sudah commit.
     */
    private void startProcess(LoanApplication loanApplication) {
        Long loanApplicationId = loanApplication.getId();

        CreateLoanApplicationCommand payload = new CreateLoanApplicationCommand(
                loanApplication.getApplicant().getId(),
                loanApplication.getLoanAmountRequested(),
                loanApplication.getLoanTenorMonths(),
                loanApplication.getPurpose(),
                loanApplication.getMonthlyIncome(),
                loanApplication.getMonthlyDebtObligation()
        );

        String businessKey = businessKeyOf(loanApplicationId);
        String correlationId = "los-lite-submit-" + loanApplicationId;

        WorkflowTriggerResult result = workflowEngineClient.startProcess(businessKey, correlationId, Map.of("loanApplicationId", payload));

        transactionTemplate.executeWithoutResult(status -> {
            LoanApplication managed = loanApplicationService.getById(loanApplicationId);
            managed.setWorkflowTriggerId(result.triggerId());
            managed.setWorkflowProcessInstanceId(result.camundaProcessInstanceId());
            managed.setWorkflowStatus(result.status());
            loanApplicationPersist.save(managed);
        });

        if (result.triggerId() == null) {
            log.warn("Trigger process WORKFLOW tidak berhasil untuk loanApplicationId={} - lanjut tanpa orkestrasi otomatis, endpoint manual (/document-verification, /scoring, /review, /disburse) tetap bisa dipakai", loanApplicationId);
        } else if ("FAILED".equals(result.status())) {
            log.warn("Trigger process WORKFLOW tercatat FAILED untuk loanApplicationId={} (triggerId={}) - perlu ditindaklanjuti manual/operator", loanApplicationId, result.triggerId());
        }
    }

    /**
     * Fase 2 dari {@link #review}/{@link #disburse}: cari lalu complete task terkait di
     * WORKFLOW, tanpa transaksi terbuka.
     */
    private void completeUserTask(LoanApplication loanApplication, String taskDefinitionKey, Map<String, Object> variables) {
        if (loanApplication.getWorkflowTriggerId() == null) {
            return;
        }

        String businessKey = businessKeyOf(loanApplication.getId());
        List<WorkflowTaskInfo> openTasks = workflowEngineClient.findOpenTasks(businessKey);

        Optional<WorkflowTaskInfo> task = openTasks.stream().filter(t -> taskDefinitionKey.equals(t.taskDefinitionKey())).findFirst();

        if (task.isEmpty()) {
            log.warn("Tidak ada task '{}' terbuka untuk businessKey={} (loanApplicationId={}) - complete di-skip", taskDefinitionKey, businessKey, loanApplication.getId());
            return;
        }

        workflowEngineClient.completeTask(task.get().id(), variables);
    }

    /**
     * Fase 2 dari {@link #moveToDocumentVerification}/{@link #runScoring}: selesaikan task
     * terkait di WORKFLOW, tanpa transaksi terbuka. Guard {@code workflowTriggerId} sama
     * persis seperti {@link #completeUserTask}: kalau trigger process belum pernah
     * tercatat, tidak ada gunanya panggil WORKFLOW sama sekali.
     */
    private void completeExternalTask(LoanApplication loanApplication, String topic, Map<String, Object> variables) {
        if (loanApplication.getWorkflowTriggerId() == null) {
            return;
        }

        String businessKey = businessKeyOf(loanApplication.getId());
        workflowEngineClient.completeExternalTask(businessKey, topic, variables);
    }
}
