package org.project.loslite.interfaces.dto;

import org.project.loslite.domain.enums.DocumentType;
import org.project.loslite.domain.enums.OcrStatus;

import java.time.Instant;

public record DocumentResponse(
        Long id,
        Long loanApplicationId,
        DocumentType documentType,
        String filePath,
        OcrStatus ocrStatus,
        OcrResultResponse ocrResult,
        Instant uploadedAt
) {
}
