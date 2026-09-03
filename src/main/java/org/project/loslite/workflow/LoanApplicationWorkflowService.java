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
 * Orchestrator penuh untuk setiap use-case LoanApplication yang terikat ke business
 * process "LOAN_ORIGINATION" yang dijalankan WORKFLOW-APP (aplikasi terpisah, Camunda 7
 * eksternal lewat REST-nya, lihat dokumentasi integrasi) - dua arah sekaligus, SATU tempat:
 * <pre>
 * Domain -> WORKFLOW-APP (kita yang panggil REST-nya):
 *    submit()                     -> trigger business process saat pengajuan disubmit
 *    moveToDocumentVerification() -> complete EXTERNAL TASK topic "document-verification"
 *    runScoring()                 -> complete EXTERNAL TASK topic "run-scoring"
 *    review()                     -> complete User Task "Officer Review" (jalur MANUAL_REVIEW)
 *    disburse()                   -> complete User Task "Disburse Funds"
 * </pre>
 * Kenapa digabung di 1 class (bukan dipisah lagi jadi "ApplicationService" murni yang
 * dibungkus WorkflowService, atau dihubungkan lewat event): supaya cuma ADA SATU tempat
 * yang tahu detail {@link WorkflowEngineClient} (trigger process, cari & complete user
 * task) - dependency cuma satu arah, class ini yang depend ke
 * {@link LoanApplicationService} (untuk delegasikan logika non-workflow: perpindahan
 * status), BUKAN sebaliknya. LoanApplicationService sendiri tidak pernah tahu WORKFLOW-APP
 * itu ada.
 * <p>
 * {@link WorkflowEngineClient} best-effort SEPENUHNYA di sisi implementasinya
 * ({@link org.project.loslite.component.WorkflowAppClient}) - broker mati/unreachable
 * TIDAK PERNAH bikin request di sini gagal: perubahan status di database (lewat
 * {@link LoanApplicationStatusService}) tetap sah duluan, orkestrasi WORKFLOW-APP cuma
 * "mengikuti" secara best-effort. TIDAK ADA lagi endpoint manual yang bypass class ini -
 * /document-verification & /scoring (lihat LoanApplicationController) SAMA-SAMA lewat sini
 * karena keduanya Camunda EXTERNAL TASK di BPMN ({@code camunda:type="external"}, topic
 * "document-verification" & "run-scoring") - lihat {@link #moveToDocumentVerification}/
 * {@link #runScoring}.
 * <p>
 * <strong>Batas transaksi - baca ini sebelum ubah {@link #submit}/{@link #review}/
 * {@link #disburse}:</strong> ketiganya TIDAK dianotasi {@code @Transactional} di level
 * method. {@link WorkflowEngineClient} (implementasinya, {@code WorkflowAppClient}) manggil
 * WORKFLOW-APP lewat WebClient SECARA BLOCKING ({@code .block()}) - kalau method ini
 * dianotasi {@code @Transactional}, koneksi JDBC dari pool akan tersandera selama seluruh
 * durasi HTTP call itu (bukan cuma kalau gagal - kalau WORKFLOW-APP lambat pun connection
 * pool LOS-LITE bisa habis dan endpoint lain yang sama sekali tidak berhubungan ikut macet).
 * Karena itu tiap use-case dipecah 2 fase eksplisit, masing-masing transaksinya sendiri
 * lewat {@link TransactionTemplate} (bukan {@code @Transactional}, karena
 * {@link #startProcess}/{@link #completeUserTask} dipanggil dari method publik di class
 * yang sama - self-invocation membuat proxy {@code @Transactional} Spring diam-diam tidak
 * berlaku, persis alasan yang sama kenapa {@code BusinessProcessTriggerService} di
 * WORKFLOW-APP juga pakai {@link TransactionTemplate}, bukan anotasi):
 * <ol>
 *     <li><strong>Fase status lokal</strong> (transaksi pendek, commit duluan): ubah status
 *     {@link LoanApplication} lewat {@link LoanApplicationStatusService}, simpan. Ini yang
 *     jadi kebenaran domain LOS-LITE - sah terlepas dari apa pun yang terjadi ke
 *     WORKFLOW-APP setelahnya.</li>
 *     <li><strong>Fase panggil WORKFLOW-APP</strong> (tanpa transaksi terbuka): panggil
 *     {@link WorkflowEngineClient}. Untuk {@link #submit}, hasilnya (triggerId,
 *     camundaProcessInstanceId, status) ditulis balik ke {@link LoanApplication} dalam
 *     transaksi pendek terpisah lagi setelahnya.</li>
 * </ol>
 * <p>
 * {@code TASK_DEFINITION_*}/{@code EXTERNAL_TASK_TOPIC_*} di bawah HARUS SAMA PERSIS
 * dengan {@code id}/{@code camunda:topic} elemen BPMN pada proses
 * {@code loan-application-process} yang di-deploy WORKFLOW-APP - dikonfirmasi dari XML
 * BPMN-nya langsung (BUKAN lagi dari {@code docs/workflow-form-generator/*.json}, desain
 * lama yang nilainya beda dan sudah tidak dipakai acuan). Kalau BPMN-nya di-redeploy
 * dengan id/topic yang beda, konstanta ini WAJIB disesuaikan ulang - tidak ada validasi
 * otomatis yang mendeteksi mismatch ini selain gejala tidak langsung: task/gateway
 * terkait tidak pernah ke-complete/ke-route, cuma kelihatan dari log warn
 * {@link #completeUserTask}/{@link #completeExternalTask}.
 */
@Component
@RequiredArgsConstructor

public class LoanApplicationWorkflowService {

    private static final Logger log = LoggerFactory.getLogger(LoanApplicationWorkflowService.class);

    // taskDefinitionKey = id elemen <bpmn:userTask> di BPMN "loan-application-process"
    // (tidak ada camunda:formKey/override lain, jadi default Camunda berlaku: id ==
    // taskDefinitionKey) - dikonfirmasi dari XML BPMN yang dideploy WORKFLOW-APP.
    private static final String TASK_DEFINITION_OFFICER_REVIEW = "UserTask_OfficerReview";
    private static final String TASK_DEFINITION_DISBURSE_FUNDS = "UserTask_DisburseFunds";

    // Camunda EXTERNAL TASK topic (camunda:type="external" di BPMN) - BEDA namespace dari
    // TASK_DEFINITION_* di atas (User Task) meskipun sama-sama "identitas node BPMN" -
    // lihat completeExternalTask() vs completeUserTask().
    private static final String EXTERNAL_TASK_TOPIC_DOCUMENT_VERIFICATION = "document-verification";
    private static final String EXTERNAL_TASK_TOPIC_RUN_SCORING = "run-scoring";

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
        // WORKFLOW-APP tidak boleh menyandera koneksi JDBC yang sedang terbuka.
        startProcess(saved);

        return saved;
    }


    /**
     * Tandai verifikasi dokumen selesai. Fase 1 REUSE {@link LoanApplicationService#moveToDocumentVerification}
     * apa adanya (bukan inline {@link TransactionTemplate} seperti {@link #submit}/
     * {@link #review}/{@link #disburse}) - manggilnya lewat bean LAIN (bukan
     * self-invocation di class ini), jadi proxy {@code @Transactional} method itu beneran
     * berlaku dan commit duluan sebelum Fase 2 di bawah jalan, tanpa perlu duplikasi
     * boilerplate transaksi.
     * <p>
     * Node "Verifikasi Dokumen" di BPMN adalah Camunda EXTERNAL TASK
     * ({@code camunda:type="external"}, topic {@code "document-verification"}) - BEDA
     * dari User Task "Officer Review"/"Disburse Funds" ({@link #completeUserTask}), makanya
     * Fase 2 lewat {@link #completeExternalTask} yang fetch+lock+complete external task
     * secara sinkron di WORKFLOW-APP, bukan {@link WorkflowEngineClient#completeTask}.
     */
    public LoanApplication moveToDocumentVerification(Long loanApplicationId) {
        LoanApplication saved = loanApplicationService.moveToDocumentVerification(loanApplicationId);

        completeExternalTask(saved, EXTERNAL_TASK_TOPIC_DOCUMENT_VERIFICATION, Map.of());

        return saved;
    }

    /**
     * Jalankan scoring, lalu selesaikan Camunda EXTERNAL TASK topic {@code "run-scoring"}
     * di WORKFLOW-APP - kirim variable {@code decision} (nilai {@link ScoringDecision})
     * supaya gateway "Keputusan Scoring?" bisa route: {@code APPROVE} -> LANGSUNG ke User
     * Task "Disburse Funds" (lihat {@link #disburse}, SKIP Officer Review sama sekali -
     * ini keputusan final rule engine, TIDAK diubah oleh method ini), {@code REJECT} ->
     * end event Ditolak, selain itu ({@code MANUAL_REVIEW}, tidak match condition apa pun)
     * -> jatuh ke default flow gateway, ke User Task "Officer Review" (lihat
     * {@link #review}). Variable name & mapping ini dikonfirmasi dari XML BPMN yang
     * dideploy WORKFLOW-APP (gateway {@code Gateway_ScoringDecision}) - JANGAN diubah
     * tanpa cek ulang XML-nya.
     * <p>
     * Fase 1 REUSE {@link ScoringService#score} apa adanya (pola sama seperti
     * {@link #moveToDocumentVerification} - cross-bean call, proxy {@code @Transactional}
     * beneran berlaku, commit duluan sebelum Fase 2). {@code loanApplication} di-load ULANG
     * setelah Fase 1 (bukan reuse instance apa pun) karena {@link ScoringOutcome} tidak
     * membawa entity-nya.
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
     * businessKey yang dikirim ke WORKFLOW-APP - deterministik dari id, TIDAK disimpan
     * sebagai kolom terpisah (lihat komentar di LoanApplication#workflowTriggerId).
     */
    private String businessKeyOf(Long loanApplicationId) {
        return "LOAN-" + loanApplicationId;
    }

    /**
     * Fase 2 dari {@link #submit}: panggil WORKFLOW-APP (tanpa transaksi terbuka - lihat
     * Javadoc class ini), lalu tulis balik hasilnya ({@code triggerId},
     * {@code camundaProcessInstanceId}, {@code status}) dalam transaksi pendek terpisah.
     * {@code loanApplication} yang di-load ulang di sini (bukan reuse instance yang
     * dipassing) supaya entity yang di-{@code save} benar-benar attached ke transaksi baru
     * ini, bukan instance detached dari transaksi {@link #submit} yang sudah commit.
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
            log.warn("Trigger business process WORKFLOW-APP tidak berhasil untuk loanApplicationId={} - lanjut tanpa orkestrasi otomatis, endpoint manual (/document-verification, /scoring, /review, /disburse) tetap bisa dipakai", loanApplicationId);
        } else if ("FAILED".equals(result.status())) {
            // WORKFLOW-APP TIDAK retry otomatis - ProcessReconciliationService yang dulu
            // menangani ini sudah dihapus dari WORKFLOW-APP (simplifikasi arsitektur).
            // Trigger FAILED akan diam selamanya sampai ada yang manggil manual
            // POST /api/v1/business-processes/id/{triggerId}/retry di WORKFLOW-APP -
            // LOS-LITE saat ini tidak punya job/mekanisme yang melakukan itu otomatis.
            log.warn("Trigger business process WORKFLOW-APP tercatat FAILED untuk loanApplicationId={} (triggerId={}) - WORKFLOW-APP TIDAK retry otomatis; perlu retry manual lewat WORKFLOW-APP atau ditindaklanjuti operator", loanApplicationId, result.triggerId());
        }
    }

    /**
     * Fase 2 dari {@link #review}/{@link #disburse}: cari lalu complete User Task terkait
     * di WORKFLOW-APP, tanpa transaksi terbuka - lihat Javadoc class ini. Tidak menulis
     * apa pun balik ke database (tidak ada state WORKFLOW-APP-related yang perlu dicatat
     * di sini, beda dengan {@link #startProcess}), jadi tidak perlu fase transaksi lagi
     * setelahnya.
     */
    private void completeUserTask(LoanApplication loanApplication, String taskDefinitionKey, Map<String, Object> variables) {
        if (loanApplication.getWorkflowTriggerId() == null) {
            return;
        }

        String businessKey = businessKeyOf(loanApplication.getId());
        List<WorkflowTaskInfo> openTasks = workflowEngineClient.findOpenTasks(businessKey);

        Optional<WorkflowTaskInfo> task = openTasks.stream().filter(t -> taskDefinitionKey.equals(t.taskDefinitionKey())).findFirst();

        if (task.isEmpty()) {
            log.warn("Tidak ada user task '{}' terbuka untuk businessKey={} (loanApplicationId={}) - complete di-skip", taskDefinitionKey, businessKey, loanApplication.getId());
            return;
        }

        workflowEngineClient.completeTask(task.get().id(), variables);
    }

    /**
     * Fase 2 dari {@link #moveToDocumentVerification}: selesaikan Camunda EXTERNAL TASK
     * terkait di WORKFLOW-APP, tanpa transaksi terbuka - lihat Javadoc class ini. Guard
     * {@code workflowTriggerId} sama persis seperti {@link #completeUserTask}: kalau
     * trigger business process belum pernah tercatat, tidak ada gunanya panggil
     * WORKFLOW-APP sama sekali (juga mencegah salah satu dari beberapa penyebab 404 yang
     * digabung jadi satu errorCode di endpoint WORKFLOW-APP - lihat javadoc
     * {@link WorkflowEngineClient#completeExternalTask}).
     */
    private void completeExternalTask(LoanApplication loanApplication, String topic, Map<String, Object> variables) {
        if (loanApplication.getWorkflowTriggerId() == null) {
            return;
        }

        String businessKey = businessKeyOf(loanApplication.getId());
        workflowEngineClient.completeExternalTask(businessKey, topic, variables);
    }
}
