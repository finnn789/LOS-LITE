package org.project.loslite.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.project.loslite.dto.CreateLoanApplicationCommand;
import org.project.loslite.dto.ReviewLoanApplicationRequest;
import org.project.loslite.service.LoanApplicationService;
import org.project.loslite.service.ScoringService;
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

/**
 * Pintu masuk HTTP untuk siklus hidup LoanApplication: create (draft) -> submit ->
 * document verification -> scoring -> review (jalur MANUAL_REVIEW) -> disburse. Setiap
 * endpoint di sini murni terima request, panggil use-case service yang sesuai, bungkus
 * hasil jadi response - tidak ada logika bisnis/state-machine sama sekali di sini (itu
 * ada di application/domain layer). submit/review/disburse sengaja lewat
 * {@link LoanApplicationWorkflowService} (bukan LoanApplicationService) karena
 * ketiganya memicu orkestrasi Camunda - lihat javadoc class itu.
 */
@RestController
@RequestMapping("/loan-applications")
@RequiredArgsConstructor
public class LoanApplicationController {

    private final LoanApplicationService loanApplicationService;
    private final LoanApplicationWorkflowService loanApplicationWorkflowService;
    private final ScoringService scoringService;

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

    @PostMapping("/{id}/submit")
    public ResponseEntity<ApiResponse<LoanApplicationResponse>> submit(@PathVariable Long id) {
        LoanApplication submitted = loanApplicationWorkflowService.submit(id);
        return ResponseEntity.ok(ApiResponse.success("Pengajuan berhasil disubmit", toResponse(submitted)));
    }

    @PostMapping("/{id}/document-verification")
    public ResponseEntity<ApiResponse<LoanApplicationResponse>> moveToDocumentVerification(@PathVariable Long id) {
        LoanApplication moved = loanApplicationService.moveToDocumentVerification(id);
        return ResponseEntity.ok(ApiResponse.success("Pengajuan masuk tahap verifikasi dokumen", toResponse(moved)));
    }

    @PostMapping("/{id}/scoring")
    public ResponseEntity<ApiResponse<ScoringResponse>> runScoring(@PathVariable Long id) {
        ScoringOutcome outcome = scoringService.score(id);

        ScoringResponse response = new ScoringResponse(
                outcome.dtiRatio(),
                outcome.scoreBucket(),
                outcome.decision(),
                outcome.ruleTrace()
        );

        return ResponseEntity.ok(ApiResponse.success("Scoring selesai dijalankan", response));
    }

    // Menutup jalur MANUAL_REVIEW (lihat ScoringService#applyDecisionToLoanStatus) - staff
    // approve/reject manual, lewat LoanApplicationWorkflowService supaya User Task
    // "Officer Review" di Tasklist (kalau proses Camunda-nya jalan) ikut ke-complete.
    @PostMapping("/{id}/review")
    public ResponseEntity<ApiResponse<LoanApplicationResponse>> review(
            @PathVariable Long id, @Valid @RequestBody ReviewLoanApplicationRequest request) {

        LoanApplication reviewed = loanApplicationWorkflowService.review(id, request.decision());
        return ResponseEntity.ok(ApiResponse.success("Keputusan review berhasil disimpan", toResponse(reviewed)));
    }

    @PostMapping("/{id}/disburse")
    public ResponseEntity<ApiResponse<LoanApplicationResponse>> disburse(@PathVariable Long id) {
        LoanApplication disbursed = loanApplicationWorkflowService.disburse(id);
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
