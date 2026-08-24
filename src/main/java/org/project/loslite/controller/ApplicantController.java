package org.project.loslite.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.project.loslite.dto.CreateApplicantCommand;
import org.project.loslite.service.ApplicantService;
import org.project.loslite.model.Applicant;
import org.project.loslite.dto.ApiResponse;
import org.project.loslite.dto.ApplicantResponse;
import org.project.loslite.dto.CreateApplicantRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.project.loslite.dto.ApplicantSummaryResponse;
import java.util.List;

/**
 * Endpoint untuk OFFICER menginput data pengaju (Applicant) - master data nasabah,
 * terpisah dari LoanApplication (satu Applicant bisa punya banyak pengajuan pinjaman).
 */
@RestController
@RequestMapping("/applicants")
@RequiredArgsConstructor
public class ApplicantController {

    private final ApplicantService applicantService;

    @PostMapping
    public ResponseEntity<ApiResponse<ApplicantResponse>> create(
            @Valid @RequestBody CreateApplicantRequest request) {

        CreateApplicantCommand command = new CreateApplicantCommand(
                request.fullName(),
                request.nik(),
                request.dateOfBirth(),
                request.phoneNumber(),
                request.email(),
                request.address()
        );

        Applicant created = applicantService.create(command);

        ApplicantResponse response = new ApplicantResponse(
                created.getId(),
                created.getFullName(),
                created.getNik(),
                created.getDateOfBirth(),
                created.getPhoneNumber(),
                created.getEmail(),
                created.getAddress()
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Applicant berhasil didaftarkan", response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ApplicantResponse>> getById(@PathVariable Long id) {
        Applicant applicant = applicantService.getById(id);

        ApplicantResponse response = new ApplicantResponse(
                applicant.getId(),
                applicant.getFullName(),
                applicant.getNik(),
                applicant.getDateOfBirth(),
                applicant.getPhoneNumber(),
                applicant.getEmail(),
                applicant.getAddress()
        );

        return ResponseEntity.ok(ApiResponse.success("Berhasil mengambil data applicant", response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ApplicantSummaryResponse>>> getAll() {
        List<ApplicantSummaryResponse> responses = applicantService.getAll()
                .stream()
                .map(a -> new ApplicantSummaryResponse(
                        a.getId(),
                        a.getFullName(),
                        a.getDateOfBirth(),
                        a.getPhoneNumber(),
                        a.getEmail(),
                        a.getAddress()
                ))
                .toList();

        return ResponseEntity.ok(ApiResponse.success("Berhasil mengambil semua applicant", responses));
    }
}
