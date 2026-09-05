package org.project.loslite.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.project.loslite.dto.ApplicantNikResponse;
import org.project.loslite.dto.CreateApplicantCommand;
import org.project.loslite.dto.UpdateApplicantCommand;
import org.project.loslite.dto.UpdateApplicantRequest;
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

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ApplicantResponse>> update(
            @PathVariable Long id, @Valid @RequestBody UpdateApplicantRequest request) {

        UpdateApplicantCommand command = new UpdateApplicantCommand(
                id,
                request.fullName(),
                request.nik(),
                request.dateOfBirth(),
                request.phoneNumber(),
                request.email(),
                request.address()
        );

        Applicant updated = applicantService.update(command);

        ApplicantResponse response = new ApplicantResponse(
                updated.getId(),
                updated.getFullName(),
                updated.getNik(),
                updated.getDateOfBirth(),
                updated.getPhoneNumber(),
                updated.getEmail(),
                updated.getAddress()
        );

        return ResponseEntity.ok(ApiResponse.success("Applicant berhasil diperbarui", response));
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

    // Lookup pakai NIK, bukan id - supaya klien (form dinamis) bisa resolve applicantId
    // sebelum POST /loan-applications cukup modal NIK (yang user pasti tahu), tanpa perlu
    // scroll/cari manual di GET /applicants (list semua) buat nemuin id-nya.
    @GetMapping("/by-nik/{nik}")
    public ResponseEntity<ApiResponse<ApplicantResponse>> getByNik(@PathVariable String nik) {
        Applicant applicant = applicantService.getByNik(nik);

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

    // NIK + nama + alamat semua applicant terdaftar - dipakai form/dropdown yang mau
    // nawarin pilihan NIK yang sudah ada (mis. sebelum panggil GET /applicants/by-nik/{nik})
    // sekalian nampilin nama/alamatnya biar gampang dikenali user, tanpa perlu narik
    // seluruh field ApplicantSummaryResponse (tanggal lahir/telepon/email) yang tidak
    // relevan buat use-case ini. Path literal "/niks" (bukan {sesuatu}), jadi tidak bentrok
    // dengan "/{id}" - Spring selalu utamakan path literal yang cocok persis dibanding path
    // variable, apapun urutan deklarasinya.
    @GetMapping("/niks")
    public ResponseEntity<ApiResponse<List<ApplicantNikResponse>>> getAllNiks() {
        List<ApplicantNikResponse> responses = applicantService.getAll()
                .stream()
                .map(a -> new ApplicantNikResponse(a.getId(), a.getNik(), a.getFullName(), a.getAddress()))
                .toList();

        return ResponseEntity.ok(ApiResponse.success("Berhasil mengambil NIK, nama, dan alamat semua applicant", responses));
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
