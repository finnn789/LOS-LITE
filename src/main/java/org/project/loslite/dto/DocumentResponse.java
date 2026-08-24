package org.project.loslite.dto;

import org.project.loslite.enums.DocumentType;
import org.project.loslite.enums.OcrStatus;

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
