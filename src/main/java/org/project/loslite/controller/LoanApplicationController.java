package org.project.loslite.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.project.loslite.dto.CreateLoanApplicationCommand;
import org.project.loslite.service.LoanApplicationService;
import org.project.loslite.service.ScoringService;
import org.project.loslite.model.LoanApplication;
import org.project.loslite.service.ScoringOutcome;
import org.project.loslite.dto.ApiResponse;
import org.project.loslite.dto.CreateLoanApplicationRequest;
import org.project.loslite.dto.LoanApplicationResponse;
import org.project.loslite.dto.ScoringResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Pintu masuk HTTP untuk siklus hidup LoanApplication: create (draft) -> submit ->
 * document verification -> scoring. Setiap endpoint di sini murni terima request,
 * panggil use-case service yang sesuai, bungkus hasil jadi response - tidak ada
 * logika bisnis/state-machine sama sekali di sini (itu ada di application/domain layer).
 */
@RestController
@RequestMapping("/loan-applications")
@RequiredArgsConstructor
public class LoanApplicationController {

    private final LoanApplicationService loanApplicationService;
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
        LoanApplication submitted = loanApplicationService.submit(id);
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

    // Dipakai berulang oleh 4 endpoint di atas (create/getById/submit/document-verification) -
    // tetap method PRIVATE biasa di controller ini (gaya 1: mapping manual, bukan factory
    // method di dalam DTO), cuma diekstrak supaya tidak copy-paste konstruktor yang sama 4x.
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
