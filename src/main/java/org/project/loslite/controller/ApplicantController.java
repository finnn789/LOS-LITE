package org.project.loslite.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.project.loslite.dto.ApiResponse;
import org.project.loslite.dto.ApplicantDetailView;
import org.project.loslite.dto.ApplicantResponse;
import org.project.loslite.dto.ApplicantSummaryView;
import org.project.loslite.dto.CreateApplicantRequest;
import org.project.loslite.dto.ListApplicantRequest;
import org.project.loslite.service.ApplicantService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

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
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ApplicantResponse> create(@Valid @RequestBody CreateApplicantRequest request) {
        return applicantService.create(request);
    }

    @GetMapping("/{id}")
    public ApiResponse<ApplicantDetailView> detail(@PathVariable Long id) {
        return applicantService.detail(id);
    }

    @GetMapping
    public ApiResponse<List<ApplicantSummaryView>> list(ListApplicantRequest filter) {
        return applicantService.list(filter);
    }
}