package org.project.loslite.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.project.loslite.dto.CreateLoanApplicationCommand;
import org.project.loslite.dto.LoanApplicationIdRequest;
import org.project.loslite.dto.ReviewLoanApplicationRequest;
import org.project.loslite.dto.UpdateLoanApplicationCommand;
import org.project.loslite.dto.UpdateLoanApplicationRequest;
import org.project.loslite.service.LoanApplicationService;
import org.project.loslite.model.LoanApplication;
import org.project.loslite.service.ScoringOutcome;
import org.project.loslite.dto.ApiResponse;
import org.project.loslite.dto.CreateLoanApplicationRequest;
import org.project.loslite.dto.LoanApplicationResponse;
import org.project.loslite.dto.ScoringResponse;
import org.project.loslite.workflow.LoanApplicationWorkflowService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Pintu masuk HTTP untuk siklus hidup LoanApplication: create (draft) -> submit ->
 * document verification -> scoring -> review (jalur MANUAL_REVIEW) -> disburse. Setiap
 * endpoint di sini murni terima request, panggil use-case service yang sesuai, bungkus
 * hasil jadi response - tidak ada logika bisnis/state-machine sama sekali di sini (itu
 * ada di application/domain layer). submit/document-verification/scoring/review/disburse
 * sengaja lewat {@link LoanApplicationWorkflowService} (bukan LoanApplicationService/
 * ScoringService langsung) karena semuanya memicu orkestrasi WORKFLOW - lihat javadoc
 * class itu. Id pengajuan dikirim lewat body ({@link org.project.loslite.dto.LoanApplicationIdRequest}
 * atau {@link ReviewLoanApplicationRequest}), BUKAN path variable - beda dari
 * {@link #getById} yang tetap {@code /{id}} (murni GET, tidak ada body).
 */
@RestController
@RequestMapping("/loan-applications")
@RequiredArgsConstructor
public class LoanApplicationController {

    private final LoanApplicationService loanApplicationService;
    private final LoanApplicationWorkflowService loanApplicationWorkflowService;

    @PostMapping
    public ResponseEntity<ApiResponse<LoanApplicationResponse>> create(
            @Valid @RequestBody CreateLoanApplicationRequest request) {

        CreateLoanApplicationCommand command = new CreateLoanApplicationCommand(
                request.applicantId(),
                request.loanAmountRequested(),
                request.loanTenorMonths(),
                request.purpose(),
                request.monthlyIncome(),
                request.monthlyDebtObligation()
        );

        LoanApplication created = loanApplicationService.create(command);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Pengajuan berhasil dibuat (draft)", toResponse(created)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<LoanApplicationResponse>> getById(@PathVariable Long id) {
        LoanApplication loanApplication = loanApplicationService.getById(id);
        return ResponseEntity.ok(ApiResponse.success("Berhasil mengambil data pengajuan", toResponse(loanApplication)));
    }

    // Cari id LoanApplication milik seorang applicant - dipakai kalau kamu cuma modal
    // applicantId (mis. hasil ApplicantController#getByNik) dan belum/lupa id pengajuannya,
    // supaya bisa lanjut submit/document-verification/scoring/review/disburse tanpa perlu
    // scroll manual atau nyimpen id dari response create sebelumnya.
    @GetMapping("/by-applicant/{applicantId}")
    public ResponseEntity<ApiResponse<List<LoanApplicationResponse>>> getByApplicantId(@PathVariable Long applicantId) {
        List<LoanApplicationResponse> responses = loanApplicationService.getByApplicantId(applicantId)
                .stream()
                .map(this::toResponse)
                .toList();

        return ResponseEntity.ok(ApiResponse.success("Berhasil mengambil pengajuan milik applicant", responses));
    }

    // Ringkasan by-nik + by-applicant jadi SATU panggilan - klien cukup modal NIK, tidak
    // perlu resolve applicantId dulu lewat ApplicantController#getByNik baru query ke sini
    // lagi. Cocok dipakai form/dashboard yang mulai dari NIK dan langsung mau lihat semua
    // pengajuan (buat pilih salah satu id-nya, lanjut submit/dst).
    @GetMapping("/by-nik/{nik}")
    public ResponseEntity<ApiResponse<List<LoanApplicationResponse>>> getByApplicantNik(@PathVariable String nik) {
        List<LoanApplicationResponse> responses = loanApplicationService.getByApplicantNik(nik)
                .stream()
                .map(this::toResponse)
                .toList();

        return ResponseEntity.ok(ApiResponse.success("Berhasil mengambil pengajuan milik applicant", responses));
    }

    // Cuma field bisnis (jumlah/tenor/tujuan/penghasilan/utang) - lihat javadoc
    // UpdateLoanApplicationRequest kenapa applicantId & status TIDAK ada di sini.
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<LoanApplicationResponse>> update(
            @PathVariable Long id, @Valid @RequestBody UpdateLoanApplicationRequest request) {

        UpdateLoanApplicationCommand command = new UpdateLoanApplicationCommand(
                id,
                request.loanAmountRequested(),
                request.loanTenorMonths(),
                request.purpose(),
                request.monthlyIncome(),
                request.monthlyDebtObligation()
        );

        LoanApplication updated = loanApplicationService.update(command);
        return ResponseEntity.ok(ApiResponse.success("Pengajuan berhasil diperbarui", toResponse(updated)));
    }

    @PostMapping("/submit")
    public ResponseEntity<ApiResponse<LoanApplicationResponse>> submit(@Valid @RequestBody LoanApplicationIdRequest request) {
        LoanApplication submitted = loanApplicationWorkflowService.submit(request.id());
        return ResponseEntity.ok(ApiResponse.success("Pengajuan berhasil disubmit", toResponse(submitted)));
    }

    // Lewat LoanApplicationWorkflowService (bukan LoanApplicationService langsung) - node
    // "Verifikasi Dokumen" di BPMN WORKFLOW adalah SERVICE_TASK topic "verifikasi_dokumen",
    // endpoint ini yang memicu penyelesaiannya, lihat
    // LoanApplicationWorkflowService#moveToDocumentVerification.
    @PostMapping("/document-verification")
    public ResponseEntity<ApiResponse<LoanApplicationResponse>> moveToDocumentVerification(@Valid @RequestBody LoanApplicationIdRequest request) {
        LoanApplication moved = loanApplicationWorkflowService.moveToDocumentVerification(request.id());

        return ResponseEntity.ok(ApiResponse.success("Pengajuan masuk tahap verifikasi dokumen", toResponse(moved)));
    }

    // Lewat LoanApplicationWorkflowService (bukan ScoringService langsung) - node
    // "Jalankan Scoring" di BPMN WORKFLOW adalah SERVICE_TASK topic "jalankan_scoring",
    // endpoint ini yang memicu penyelesaiannya (kirim variable "decision" supaya gateway
    // "Keputusan Scoring?" ikut ke-route), lihat LoanApplicationWorkflowService#runScoring.
    @PostMapping("/scoring")
    public ResponseEntity<ApiResponse<ScoringResponse>> runScoring(@Valid @RequestBody LoanApplicationIdRequest request) {
        ScoringOutcome outcome = loanApplicationWorkflowService.runScoring(request.id());

        ScoringResponse response = new ScoringResponse(
                outcome.dtiRatio(),
                outcome.scoreBucket(),
                outcome.decision(),
                outcome.ruleTrace()
        );

        return ResponseEntity.ok(ApiResponse.success("Scoring selesai dijalankan", response));
    }

    // Menutup jalur MANUAL_REVIEW (lihat ScoringService#applyDecisionToLoanStatus) - staff
    // approve/reject manual, lewat LoanApplicationWorkflowService supaya task
    // "Officer Review" di WORKFLOW ikut ke-complete.
    @PostMapping("/review")
    public ResponseEntity<ApiResponse<LoanApplicationResponse>> review(@Valid @RequestBody ReviewLoanApplicationRequest request) {

        LoanApplication reviewed = loanApplicationWorkflowService.review(request.id(), request.decision());
        return ResponseEntity.ok(ApiResponse.success("Keputusan review berhasil disimpan", toResponse(reviewed)));
    }

    @PostMapping("/disburse")
    public ResponseEntity<ApiResponse<LoanApplicationResponse>> disburse(@Valid @RequestBody LoanApplicationIdRequest request) {
        LoanApplication disbursed = loanApplicationWorkflowService.disburse(request.id());
        return ResponseEntity.ok(ApiResponse.success("Dana berhasil dicairkan", toResponse(disbursed)));
    }

    // Dipakai berulang oleh endpoint di atas (create/getById/submit/document-verification/
    // review/disburse) - tetap method PRIVATE biasa di controller ini (gaya 1: mapping
    // manual, bukan factory method di dalam DTO), cuma diekstrak supaya tidak copy-paste
    // konstruktor yang sama berkali-kali.
    private LoanApplicationResponse toResponse(LoanApplication entity) {
        return new LoanApplicationResponse(
                entity.getId(),
                entity.getApplicant().getId(),
                entity.getLoanAmountRequested(),
                entity.getLoanTenorMonths(),
                entity.getPurpose(),
                entity.getMonthlyIncome(),
                entity.getMonthlyDebtObligation(),
                entity.getStatus(),
                entity.getSubmittedAt(),
                entity.getDecidedAt()
        );
    }
}
