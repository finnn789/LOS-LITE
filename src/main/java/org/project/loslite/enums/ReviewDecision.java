package org.project.loslite.enums;

/**
 * Keputusan manual staff (officer/admin) saat menutup User Task "Officer Review" pada
 * pengajuan yang jatuh ke jalur MANUAL_REVIEW hasil {@link ScoringDecision}. Sengaja
 * enum TERPISAH dari ScoringDecision (bukan reuse) - endpoint review cuma boleh terima
 * APPROVE/REJECT dari manusia, MANUAL_REVIEW tidak masuk akal sebagai input di sini.
 */
public enum ReviewDecision {
    APPROVE,
    REJECT
}
