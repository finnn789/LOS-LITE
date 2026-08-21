package org.project.loslite.exception;

/**
 * Dilempar saat ada percobaan pindah LoanStatus yang tidak diizinkan
 * (misal DRAFT -> DISBURSED langsung, lompat semua tahap verifikasi/scoring).
 */
public class InvalidLoanStatusTransitionException extends RuntimeException {
    public InvalidLoanStatusTransitionException(String message) {
        super(message);
    }
}
