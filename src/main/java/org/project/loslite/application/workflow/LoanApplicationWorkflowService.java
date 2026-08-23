package org.project.loslite.application.workflow;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import lombok.RequiredArgsConstructor;
import org.project.loslite.application.service.LoanApplicationService;
import org.project.loslite.application.service.ScoringOutcome;
import org.project.loslite.application.service.ScoringService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Sisi "worker" dari orkestrasi BPMN process "loan-application-process" (digambar
 * terpisah di Camunda Modeler, disimpan sebagai resource .bpmn, dideploy ke broker
 * Zeebe/C8Run). Class ini TIDAK menggambar/mendefinisikan proses - dia cuma berisi
 * method yang dipanggil OTOMATIS oleh broker Zeebe setiap kali token proses sampai
 * di Service Task tertentu.
 * <p>
 * Alur proses yang dimaksud (lihat file .bpmn untuk bentuk visualnya):
 * <pre>
 * Start (dipicu saat LoanApplication di-submit, variable "loanApplicationId")
 *    -> Service Task "document-verification"  -> method verifyDocument() di bawah
 *    -> Service Task "run-scoring"             -> method runScoring() di bawah
 *    -> Exclusive Gateway, baca variable "decision" hasil runScoring()
 *         -> APPROVE / REJECT -> End
 *         -> MANUAL_REVIEW    -> User Task Officer Review (belum diimplementasi)
 * </pre>
 * <p>
 * PRINSIP PENTING: method di sini HANYA ADAPTER, sama sekali tidak ada logika bisnis.
 * Semua keputusan (validasi transisi status, hitung DTI, aturan scoring) tetap ada di
 * LoanApplicationService/ScoringService/RuleEngine yang sudah ada - class ini cuma
 * jembatan supaya Zeebe bisa memicu mereka.
 * <p>
 * String di @JobWorker(type = "...") HARUS SAMA PERSIS (case-sensitive) dengan field
 * "Type" pada Service Task yang digambar di Camunda Modeler - itu yang jadi "alamat"
 * dipasangkannya diagram BPMN dengan method Java ini.
 */
@Component
@RequiredArgsConstructor
public class LoanApplicationWorkflowService {

    private static final Logger log = LoggerFactory.getLogger(LoanApplicationWorkflowService.class);

    private final LoanApplicationService loanApplicationService;
    private final ScoringService scoringService;

    /**
     * Menjalankan Service Task "document-verification". Saat ini CUMA pindah status
     * LoanApplication ke DOCUMENT_VERIFICATION lewat LoanApplicationService yang sudah
     * ada (belum ada pengecekan nyata dokumen sudah lengkap/OCR sukses - itu business
     * rule baru yang sebaiknya ditambah di LoanApplicationService/DocumentService
     * sendiri nanti, bukan di sini).
     * <p>
     * Tidak return apa-apa (void) karena tidak ada gateway di diagram yang butuh baca
     * hasil dari step ini - autoComplete (default true) akan complete job otomatis
     * begitu method ini selesai tanpa exception.
     */
    @JobWorker(type = "document-verification")
    public void verifyDocument(@Variable(name = "loanApplicationId") Long loanApplicationId) {
        log.info("Job 'document-verification' diterima untuk loanApplicationId={}", loanApplicationId);

        loanApplicationService.moveToDocumentVerification(loanApplicationId);
    }

    /**
     * Menjalankan Service Task "run-scoring". Panggil ScoringService.score() yang
     * sudah ada (di dalamnya sudah termasuk pindah status ke SCORING, evaluasi
     * RuleEngine, simpan ScoringResult, dan pindah status ke APPROVED/REJECTED kalau
     * keputusannya bukan MANUAL_REVIEW).
     * <p>
     * Return Map jadi output variable proses - "decision" dibaca Exclusive Gateway
     * di diagram untuk menentukan jalur berikutnya (APPROVE/REJECT/MANUAL_REVIEW),
     * di-serialize sebagai String (nama enum) supaya gampang dibandingkan di FEEL
     * expression gateway, misal: =decision = "APPROVE"
     */
    @JobWorker(type = "run-scoring")
    public Map<String, Object> runScoring(@Variable(name = "loanApplicationId") Long loanApplicationId) {
        log.info("Job 'run-scoring' diterima untuk loanApplicationId={}", loanApplicationId);

        ScoringOutcome outcome = scoringService.score(loanApplicationId);

        return Map.of(
                "decision", outcome.decision().name(),
                "dtiRatio", outcome.dtiRatio(),
                "scoreBucket", outcome.scoreBucket().name()
        );
    }
}
