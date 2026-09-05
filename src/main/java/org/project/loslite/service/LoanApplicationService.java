package org.project.loslite.service;

import lombok.RequiredArgsConstructor;
import org.project.loslite.dto.CreateLoanApplicationCommand;
import org.project.loslite.dto.UpdateLoanApplicationCommand;
import org.project.loslite.enums.LoanStatus;
import org.project.loslite.model.Applicant;
import org.project.loslite.model.LoanApplication;
import org.project.loslite.persist.ApplicantPersist;
import org.project.loslite.persist.LoanApplicationPersist;
import org.project.loslite.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Use-case orchestrator untuk bagian siklus hidup LoanApplication yang TIDAK terikat ke
 * orkestrasi Camunda: create draft & pindah ke DOCUMENT_VERIFICATION. Class ini sengaja
 * TIDAK PERNAH import apa pun dari Camunda/Zeebe - submit()/review()/disburse() (yang
 * memicu proses BPMN) ada di {@link org.project.loslite.workflow.LoanApplicationWorkflowService},
 * yang sebaliknya depend ke class ini (satu arah saja, supaya tidak circular). Tahap
 * SCORING ditangani ScoringService, karena butuh domain service (RuleEngine) yang berbeda
 * urusan.
 */
@Service
@RequiredArgsConstructor
public class LoanApplicationService {

    private final LoanApplicationPersist loanApplicationPersist;
    private final ApplicantPersist applicantPersist;
    private final ApplicantService applicantService;
    private final LoanApplicationStatusService loanApplicationStatusService;

    @Transactional
    public LoanApplication create(CreateLoanApplicationCommand command) {
        Applicant applicant = applicantPersist.findById(command.applicantId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Applicant dengan id " + command.applicantId() + " tidak ditemukan"));

        // Status TIDAK di-set di sini - LoanApplication.onCreate() (@PrePersist)
        // otomatis isi DRAFT kalau null, lihat LoanApplication.java.
        LoanApplication loanApplication = LoanApplication.builder()
                .applicant(applicant)
                .loanAmountRequested(command.loanAmountRequested())
                .loanTenorMonths(command.loanTenorMonths())
                .purpose(command.purpose())
                .monthlyIncome(command.monthlyIncome())
                .monthlyDebtObligation(command.monthlyDebtObligation())
                .build();

        return loanApplicationPersist.save(loanApplication);
    }

    // Cuma field bisnis (jumlah/tenor/tujuan/penghasilan/utang) yang bisa diedit lewat
    // sini - applicant pemilik dan status tetap tidak tersentuh (lihat javadoc
    // UpdateLoanApplicationRequest), jadi tidak perlu validasi ulang applicant atau
    // lewat LoanApplicationStatusService sama sekali.
    @Transactional
    public LoanApplication update(UpdateLoanApplicationCommand command) {
        LoanApplication loanApplication = getById(command.id());

        loanApplication.setLoanAmountRequested(command.loanAmountRequested());
        loanApplication.setLoanTenorMonths(command.loanTenorMonths());
        loanApplication.setPurpose(command.purpose());
        loanApplication.setMonthlyIncome(command.monthlyIncome());
        loanApplication.setMonthlyDebtObligation(command.monthlyDebtObligation());

        return loanApplicationPersist.save(loanApplication);
    }

    @Transactional(readOnly = true)
    public LoanApplication getById(Long id) {
        return loanApplicationPersist.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "LoanApplication dengan id " + id + " tidak ditemukan"));
    }

    // Histori pengajuan milik satu applicant - dipakai buat resolve LoanApplication id
    // (mis. buat lanjut submit/document-verification/dst) tanpa perlu simpan/hafal id
    // manual, cukup modal applicantId (yang sendirinya bisa didapat dari
    // ApplicantService#getByNik). Validasi applicant-nya ada dulu supaya error message-nya
    // jelas beda antara "applicant tidak ada" vs "applicant ada tapi belum pernah mengajukan".
    @Transactional(readOnly = true)
    public List<LoanApplication> getByApplicantId(Long applicantId) {
        if (applicantPersist.findById(applicantId).isEmpty()) {
            throw new ResourceNotFoundException(
                    "Applicant dengan id " + applicantId + " tidak ditemukan");
        }

        return loanApplicationPersist.findByApplicantId(applicantId);
    }

    // Ringkasan 1-panggilan dari getByApplicantId - resolve applicant lewat NIK dulu
    // (delegasi ke ApplicantService#getByNik, biar hash-NIK-nya tidak duplikat di sini),
    // baru ambil histori pengajuannya. Klien cukup modal NIK, tidak perlu 2 kali round-trip
    // (by-nik lalu by-applicant) buat dapat semua LoanApplication milik satu orang.
    @Transactional(readOnly = true)
    public List<LoanApplication> getByApplicantNik(String nik) {
        Applicant applicant = applicantService.getByNik(nik);
        return loanApplicationPersist.findByApplicantId(applicant.getId());
    }

    @Transactional
    public LoanApplication moveToDocumentVerification(Long id) {
        LoanApplication loanApplication = getById(id);

        return loanApplicationStatusService.changeStatus(loanApplication, LoanStatus.DOCUMENT_VERIFICATION, null);
    }
}
