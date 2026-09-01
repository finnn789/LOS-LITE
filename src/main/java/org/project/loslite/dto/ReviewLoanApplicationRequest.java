package org.project.loslite.dto;

import jakarta.validation.constraints.NotNull;
import org.project.loslite.enums.ReviewDecision;

public record ReviewLoanApplicationRequest(

        @NotNull(message = "decision wajib diisi (APPROVE atau REJECT)")
        ReviewDecision decision
) {
}
