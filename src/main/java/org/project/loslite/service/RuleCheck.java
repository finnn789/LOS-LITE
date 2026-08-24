package org.project.loslite.service;

/**
 * Satu hasil evaluasi rule tunggal — dipakai untuk membangun ruleTrace yang bisa
 * di-serialize ke JSON dan disimpan di ScoringResult.ruleTrace. Ini yang bikin
 * keputusan APPROVE/REJECT bisa dijelaskan (explainable), bukan black-box.
 *
 * @param code    kode rule, misal "R1_DTI", "R2_MIN_AGE" — dipakai untuk audit/debug
 * @param passed  true = rule ini "lolos" (tidak jadi alasan penolakan)
 * @param message penjelasan hasil dalam bahasa manusia
 */
public record RuleCheck(String code, boolean passed, String message) {
}
