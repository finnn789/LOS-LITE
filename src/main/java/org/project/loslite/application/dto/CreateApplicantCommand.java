package org.project.loslite.application.dto;

import java.time.LocalDate;

public record CreateApplicantCommand(
        String fullName,
        String nik,
        LocalDate dateOfBirth,
        String phoneNumber,
        String email,
        String address
) {
}
