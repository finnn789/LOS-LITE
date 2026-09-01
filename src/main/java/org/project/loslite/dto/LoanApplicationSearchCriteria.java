package org.project.loslite.dto;

import org.project.loslite.enums.LoanStatus;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Filter dinamis untuk {@code LoanApplicationRepository#search} (contoh query "berat"
 * pakai Blaze-Persistence). Semua field opsional/nullable - field yang null berarti filter
 * itu tidak diterapkan.
 */
public record LoanApplicationSearchCriteria(
        String applicantName,
        LoanStatus status,
        Instant submittedFrom,
        Instant submittedTo,
        BigDecimal minAmount,
        BigDecimal maxAmount
) {
}
