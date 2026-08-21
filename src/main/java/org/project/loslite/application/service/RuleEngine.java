package org.project.loslite.application.service;

import org.project.loslite.domain.enums.ScoreBucket;
import org.project.loslite.domain.enums.ScoringDecision;
import org.project.loslite.domain.model.Applicant;
import org.project.loslite.domain.model.LoanApplication;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;

/**
 * Domain service murni Java (zero Spring import) — mengevaluasi seluruh rule bisnis
 * atas satu LoanApplication, lalu menghasilkan keputusan akhir (APPROVE/REJECT/MANUAL_REVIEW)
 * beserta jejak (trace) tiap rule yang dievaluasi, supaya keputusan bisa dijelaskan.
 * <p>
 * Urutan evaluasi: rule "disqualifying" (R2-R4) dicek dulu — kalau salah satu gagal,
 * keputusan LANGSUNG REJECT apa pun hasil DTI-nya, karena dianggap kondisi yang
 * tidak bisa ditoleransi. Kalau semua lolos, baru DTI (R1) yang menentukan.
 */
public class RuleEngine {

    private static final int MIN_AGE_YEARS = 21;
    private static final int MAX_AGE_AT_PAYOFF_YEARS = 60;

    // Plafon pinjaman tidak boleh lebih dari 10x penghasilan bulanan.
    private static final BigDecimal MAX_LOAN_TO_MONTHLY_INCOME_MULTIPLIER = BigDecimal.valueOf(10);

    private final DtiCalculator dtiCalculator;

    public RuleEngine(DtiCalculator dtiCalculator) {
        this.dtiCalculator = dtiCalculator;
    }

    public ScoringOutcome evaluate(Applicant applicant, LoanApplication loanApplication) {
        List<RuleCheck> ruleTrace = new ArrayList<>();

        RuleCheck minAgeCheck = checkMinAge(applicant.getDateOfBirth());
        RuleCheck ageTenorCheck = checkAgeAtPayoff(applicant.getDateOfBirth(), loanApplication.getLoanTenorMonths());
        RuleCheck loanToIncomeCheck = checkLoanToIncome(
                loanApplication.getLoanAmountRequested(),
                loanApplication.getMonthlyIncome()
        );

        ruleTrace.add(minAgeCheck);
        ruleTrace.add(ageTenorCheck);
        ruleTrace.add(loanToIncomeCheck);

        boolean anyDisqualified = !minAgeCheck.passed() || !ageTenorCheck.passed() || !loanToIncomeCheck.passed();

        DtiResult dtiResult = dtiCalculator.calculate(
                loanApplication.getMonthlyIncome(),
                loanApplication.getMonthlyDebtObligation(),
                loanApplication.getLoanAmountRequested(),
                loanApplication.getLoanTenorMonths()
        );
        ruleTrace.add(dtiRuleCheck(dtiResult.scoreBucket(), dtiResult.dtiRatio()));

        ScoringDecision decision = anyDisqualified
                ? ScoringDecision.REJECT
                : decideFromScoreBucket(dtiResult.scoreBucket());

        return new ScoringOutcome(dtiResult.dtiRatio(), dtiResult.scoreBucket(), decision, ruleTrace);
    }

    // --- R2: usia minimal saat mengajukan ---
    private RuleCheck checkMinAge(LocalDate dateOfBirth) {
        int age = Period.between(dateOfBirth, LocalDate.now()).getYears();
        boolean passed = age >= MIN_AGE_YEARS;
        String message = passed
                ? "Usia applicant " + age + " tahun, memenuhi minimum " + MIN_AGE_YEARS + " tahun"
                : "Usia applicant " + age + " tahun, di bawah minimum " + MIN_AGE_YEARS + " tahun";
        return new RuleCheck("R2_MIN_AGE", passed, message);
    }

    // --- R3: usia + tenor tidak boleh lewat usia produktif ---
    private RuleCheck checkAgeAtPayoff(LocalDate dateOfBirth, int loanTenorMonths) {
        int currentAge = Period.between(dateOfBirth, LocalDate.now()).getYears();
        int tenorYears = loanTenorMonths / 12;
        int ageAtPayoff = currentAge + tenorYears;

        boolean passed = ageAtPayoff <= MAX_AGE_AT_PAYOFF_YEARS;
        String message = passed
                ? "Usia saat pinjaman lunas diperkirakan " + ageAtPayoff + " tahun, masih dalam batas " + MAX_AGE_AT_PAYOFF_YEARS + " tahun"
                : "Usia saat pinjaman lunas diperkirakan " + ageAtPayoff + " tahun, melebihi batas " + MAX_AGE_AT_PAYOFF_YEARS + " tahun";
        return new RuleCheck("R3_AGE_AT_PAYOFF", passed, message);
    }


    private RuleCheck checkLoanToIncome(BigDecimal loanAmountRequested, BigDecimal monthlyIncome) {
        BigDecimal maxLoanAmount = monthlyIncome.multiply(MAX_LOAN_TO_MONTHLY_INCOME_MULTIPLIER);
        boolean passed = loanAmountRequested.compareTo(maxLoanAmount) <= 0;
        String message = passed
                ? "Jumlah pinjaman masih dalam batas " + MAX_LOAN_TO_MONTHLY_INCOME_MULTIPLIER + "x penghasilan bulanan"
                : "Jumlah pinjaman melebihi batas " + MAX_LOAN_TO_MONTHLY_INCOME_MULTIPLIER + "x penghasilan bulanan";
        return new RuleCheck("R4_LOAN_TO_INCOME", passed, message);
    }

    // --- R1: DTI ratio, sekadar dicatat di trace (klasifikasinya sudah dari DtiCalculator) ---
    private RuleCheck dtiRuleCheck(ScoreBucket scoreBucket, BigDecimal dtiRatio) {
        boolean passed = scoreBucket != ScoreBucket.HIGH_RISK;
        String message = "DTI ratio " + dtiRatio + " -> " + scoreBucket;
        return new RuleCheck("R1_DTI", passed, message);
    }

    private ScoringDecision decideFromScoreBucket(ScoreBucket scoreBucket) {
        return switch (scoreBucket) {
            case LOW_RISK -> ScoringDecision.APPROVE;
            case MEDIUM_RISK -> ScoringDecision.MANUAL_REVIEW;
            case HIGH_RISK -> ScoringDecision.REJECT;
        };
    }
}
