package org.project.loslite.workflow;

import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.project.loslite.dto.WorkflowTaskInfo;
import org.project.loslite.dto.WorkflowTriggerResult;
import org.project.loslite.enums.LoanStatus;
import org.project.loslite.enums.ReviewDecision;
import org.project.loslite.interfaces.WorkflowEngineClient;
import org.project.loslite.model.LoanApplication;
import org.project.loslite.repository.LoanApplicationRepository;
import org.project.loslite.service.LoanApplicationService;
import org.project.loslite.service.LoanApplicationStatusService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
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
 *    submit()   -> trigger business process saat pengajuan disubmit
 *    review()   -> complete User Task "Officer Review" (jalur MANUAL_REVIEW)
 *    disburse() -> complete User Task "Disburse Funds"
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
 * "mengikuti" secara best-effort. Endpoint manual /document-verification & /scoring
 * (lihat LoanApplicationController) TIDAK lewat class ini sama sekali - keduanya bukan
 * bagian dari kontrak WORKFLOW-APP (tidak ada job worker/callback dari WORKFLOW-APP ke
 * LOS-LITE untuk keduanya), staff/service lain memanggilnya langsung.
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
 * {@code TASK_DEFINITION_*} di bawah HARUS SAMA PERSIS dengan {@code taskDefinitionKey}
 * User Task pada BPMN yang di-deploy di WORKFLOW-APP - nilainya diwarisi dari penamaan
 * element di {@code docs/workflow-form-generator/*.json} (desain lama, belum dikonfirmasi
 * ulang dengan tim WORKFLOW-APP sejak pindah orchestrator).
 */
@Component
@RequiredArgsConstructor

public class LoanApplicationWorkflowService {

    private static final Logger log = LoggerFactory.getLogger(LoanApplicationWorkflowService.class);

    private static final String TASK_DEFINITION_OFFICER_REVIEW = "OfficerReview";
    private static final String TASK_DEFINITION_DISBURSE_FUNDS = "DisburseFunds";

    private final LoanApplicationService loanApplicationService;
    private final LoanApplicationRepository loanApplicationRepository;
    private final LoanApplicationStatusService loanApplicationStatusService;
    private final WorkflowEngineClient workflowEngineClient;
    private final TransactionTemplate transactionTemplate;


    public LoanApplication submit(Long loanApplicationId) {
        LoanApplication saved = transactionTemplate.execute(status -> {
            LoanApplication loanApplication = loanApplicationService.getById(loanApplicationId);
            loanApplicationStatusService.changeStatus(loanApplication, LoanStatus.SUBMITTED, null);
            loanApplication.setSubmittedAt(Instant.now());
            return loanApplicationRepository.save(loanApplication);
        });

        // Di luar transaksi manapun - lihat Javadoc class ini soal kenapa HTTP call ke
        // WORKFLOW-APP tidak boleh menyandera koneksi JDBC yang sedang terbuka.
        startProcess(saved);

        return saved;
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
            return loanApplicationRepository.save(loanApplication);
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
            return loanApplicationRepository.save(loanApplication);
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
        String businessKey = businessKeyOf(loanApplicationId);
        String correlationId = "los-lite-submit-" + loanApplicationId;

        WorkflowTriggerResult result = workflowEngineClient.startProcess(businessKey, correlationId, Map.of("loanApplicationId", loanApplicationId));

        transactionTemplate.executeWithoutResult(status -> {
            LoanApplication managed = loanApplicationService.getById(loanApplicationId);
            managed.setWorkflowTriggerId(result.triggerId());
            managed.setWorkflowProcessInstanceId(result.camundaProcessInstanceId());
            managed.setWorkflowStatus(result.status());
            loanApplicationRepository.save(managed);
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
}
