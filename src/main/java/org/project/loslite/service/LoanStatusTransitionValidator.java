package org.project.loslite.service;

import org.project.loslite.enums.LoanStatus;
import org.project.loslite.exception.InvalidLoanStatusTransitionException;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Domain service murni Java — SATU-SATUNYA tempat yang tahu aturan "status boleh
 * pindah ke status apa saja". Sengaja bukan if-else tersebar di berbagai service,
 * supaya aturan alur pengajuan tidak bisa dilanggar diam-diam dari tempat lain
 * mana pun di aplikasi.
 * <p>
 * Alur normal:
 * DRAFT -> SUBMITTED -> DOCUMENT_VERIFICATION -> SCORING -> APPROVED -> DISBURSED
 * SCORING -> REJECTED (jalur keluar kalau ditolak)
 * <p>
 * REJECTED dan DISBURSED sengaja TIDAK punya transisi keluar sama sekali (status akhir/terminal) -
 * kalau applicant mau coba lagi setelah ditolak, itu jadi LoanApplication BARU, bukan
 * ubah status yang sudah REJECTED.
 */
public class LoanStatusTransitionValidator {

    private static final Map<LoanStatus, Set<LoanStatus>> ALLOWED_TRANSITIONS = new EnumMap<>(LoanStatus.class);

    static {
        ALLOWED_TRANSITIONS.put(LoanStatus.DRAFT, EnumSet.of(LoanStatus.SUBMITTED));
        ALLOWED_TRANSITIONS.put(LoanStatus.SUBMITTED, EnumSet.of(LoanStatus.DOCUMENT_VERIFICATION));
        ALLOWED_TRANSITIONS.put(LoanStatus.DOCUMENT_VERIFICATION, EnumSet.of(LoanStatus.SCORING));
        ALLOWED_TRANSITIONS.put(LoanStatus.SCORING, EnumSet.of(LoanStatus.APPROVED, LoanStatus.REJECTED));
        ALLOWED_TRANSITIONS.put(LoanStatus.APPROVED, EnumSet.of(LoanStatus.DISBURSED));
        ALLOWED_TRANSITIONS.put(LoanStatus.REJECTED, EnumSet.noneOf(LoanStatus.class));
        ALLOWED_TRANSITIONS.put(LoanStatus.DISBURSED, EnumSet.noneOf(LoanStatus.class));
    }

    /**
     * @throws InvalidLoanStatusTransitionException kalau perpindahan status tidak diizinkan
     */
    public void validateTransition(LoanStatus from, LoanStatus to) {
        Set<LoanStatus> allowedNextStatuses = ALLOWED_TRANSITIONS.get(from);

        if (allowedNextStatuses == null || !allowedNextStatuses.contains(to)) {
            throw new InvalidLoanStatusTransitionException(
                    "Tidak bisa pindah status dari " + from + " ke " + to);
        }
    }
}
