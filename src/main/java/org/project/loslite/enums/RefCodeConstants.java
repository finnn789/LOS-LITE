package org.project.loslite.enums;

/**
 * Semua constant pengganti enum lama dikumpulkan di sini, dikelompokkan lewat
 * nested class per groupCode (Group.LOAN_STATUS -> LoanStatus.SUBMITTED, dst)
 * supaya tetap satu file tapi nggak numpuk jadi flat list yang gampang salah
 * pasang grup.
 * <p>
 * Ini BUKAN pengganti baris di tabel ref_code - data groupCode/code tetap hidup
 * di DB (buat label/sort order/dropdown API), class ini cuma cermin
 * compile-time-safe dari nilai yang sama, dipakai di titik-titik yang
 * MENENTUKAN nilai (state machine, target status per endpoint, hasil kalkulasi)
 * - bukan buat validasi input client (itu tugas RefCodeService.isValid, cuma
 * relevan buat DOCUMENT_TYPE dan USER_ROLE karena dua ini yang beneran datang
 * dari request client; makanya di bawah sengaja TIDAK ada nested class value
 * untuk dua grup itu).
 */
public final class RefCodeConstants {

    private RefCodeConstants() {
    }

    /** Daftar groupCode yang dikenal sistem - pengganti "nama enum class" lama. */
    public static final class Group {
        public static final String LOAN_STATUS = "LOAN_STATUS";
        public static final String DOCUMENT_TYPE = "DOCUMENT_TYPE";
        public static final String OCR_STATUS = "OCR_STATUS";
        public static final String USER_ROLE = "USER_ROLE";
        public static final String SCORE_BUCKET = "SCORE_BUCKET";
        public static final String SCORING_DECISION = "SCORING_DECISION";

        private Group() {
        }
    }

    /**
     * Value groupCode LOAN_STATUS. Status pengajuan tidak pernah dikirim bebas
     * oleh client - selalu implisit dari endpoint (/submit,
     * /document-verification, /scoring, dst).
     */
    public static final class LoanStatus {
        public static final String DRAFT = "DRAFT";
        public static final String SUBMITTED = "SUBMITTED";
        public static final String DOCUMENT_VERIFICATION = "DOCUMENT_VERIFICATION";
        public static final String SCORING = "SCORING";
        public static final String APPROVED = "APPROVED";
        public static final String REJECTED = "REJECTED";
        public static final String DISBURSED = "DISBURSED";

        private LoanStatus() {
        }
    }

    /**
     * Value groupCode OCR_STATUS. Selalu di-set sistem sendiri (default PENDING
     * saat upload, lalu PROCESSED/FAILED dari hasil panggilan OCR service).
     */
    public static final class OcrStatus {
        public static final String PENDING = "PENDING";
        public static final String PROCESSED = "PROCESSED";
        public static final String FAILED = "FAILED";

        private OcrStatus() {
        }
    }

    /** Value groupCode SCORE_BUCKET. Hasil hitungan DtiCalculator.classify(...). */
    public static final class ScoreBucket {
        public static final String LOW_RISK = "LOW_RISK";
        public static final String MEDIUM_RISK = "MEDIUM_RISK";
        public static final String HIGH_RISK = "HIGH_RISK";

        private ScoreBucket() {
        }
    }

    /** Value groupCode SCORING_DECISION. Hasil evaluasi RuleEngine.evaluate(...). */
    public static final class ScoringDecision {
        public static final String APPROVE = "APPROVE";
        public static final String REJECT = "REJECT";
        public static final String MANUAL_REVIEW = "MANUAL_REVIEW";

        private ScoringDecision() {
        }
    }
}
