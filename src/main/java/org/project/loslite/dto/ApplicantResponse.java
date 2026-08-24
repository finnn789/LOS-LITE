package org.project.loslite.dto;

import java.time.LocalDate;

public record ApplicantResponse(
        Long id,
        String fullName,
        String nik,
        LocalDate dateOfBirth,
        String phoneNumber,
        String email,
        String address
) {
}
