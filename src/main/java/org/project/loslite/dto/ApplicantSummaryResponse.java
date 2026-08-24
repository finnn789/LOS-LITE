package org.project.loslite.dto;

import java.time.LocalDate;

public record ApplicantSummaryResponse(
        Long id,
        String fullName,
        LocalDate dateOfBirth,
        String phoneNumber,
        String email,
        String address
) {
}