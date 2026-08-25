package org.project.loslite.service;

import com.blazebit.persistence.CriteriaBuilderFactory;
import com.blazebit.persistence.PagedList;
import com.blazebit.persistence.view.EntityViewManager;
import com.blazebit.persistence.view.EntityViewSetting;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.project.loslite.dto.ApiResponse;
import org.project.loslite.dto.ApplicantDetailView;
import org.project.loslite.dto.ApplicantResponse;
import org.project.loslite.dto.ApplicantSummaryView;
import org.project.loslite.dto.CreateApplicantRequest;
import org.project.loslite.dto.ListApplicantRequest;
import org.project.loslite.exception.DuplicateResourceException;
import org.project.loslite.exception.ResourceNotFoundException;
import org.project.loslite.model.Applicant;
import org.project.loslite.model.QApplicant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ApplicantService {

    private static final String LOWER_PREFIX = "LOWER(";

    @PersistenceContext
    private EntityManager em;
    private final CriteriaBuilderFactory configBuilder;
    private final EntityViewManager viewManager;

    @Transactional
    public ApiResponse<ApplicantResponse> create(CreateApplicantRequest request) {

        var nikHash = hashNik(request.nik());
        var qp = new QApplicant("a");

        // Cek duplikat lewat HASH, bukan NIK mentah. Ambil kolom id saja + LIMIT 1
        // karena yang dibutuhkan cuma "ada atau tidak", bukan isinya.
        var duplicate = configBuilder.create(em, Long.class)
                .from(Applicant.class, qp.getMetadata().getName())
                .select(qp.id.toString())
                .where(qp.nikHash.toString()).eq(nikHash)
                .setMaxResults(1)
                .getResultList();

        if (!duplicate.isEmpty()) {
            throw new DuplicateResourceException("Applicant dengan NIK ini sudah terdaftar");
        }

        var applicant = Applicant.builder()
                .fullName(request.fullName())
                .nik(request.nik())
                .nikHash(nikHash)
                .dateOfBirth(request.dateOfBirth())
                .phoneNumber(request.phoneNumber())
                .email(request.email())
                .address(request.address())
                .build();

        em.persist(applicant);

        return ApiResponse.success("Applicant berhasil didaftarkan", toResponse(applicant));
    }

    @Transactional(readOnly = true)
    public ApiResponse<ApplicantDetailView> detail(Long id) {

        var qp = new QApplicant("a");

        var query = configBuilder.create(em, Applicant.class)
                .from(Applicant.class, qp.getMetadata().getName())
                .where(qp.id.toString()).eq(id);

        var res = viewManager
                .applySetting(EntityViewSetting.create(ApplicantDetailView.class), query)
                .getResultList();

        if (res.isEmpty()) {
            throw new ResourceNotFoundException("Applicant dengan id " + id + " tidak ditemukan");
        }

        return ApiResponse.success("Berhasil mengambil data applicant", res.get(0));
    }

    @Transactional(readOnly = true)
    public ApiResponse<List<ApplicantSummaryView>> list(ListApplicantRequest filter) {

        var perPage = (filter.getPerPage() != null && filter.getPerPage() > 0) ? filter.getPerPage() : 10;
        var page = (filter.getPage() != null && filter.getPage() > 0) ? filter.getPage() : 1;
        var offset = (page - 1) * perPage;

        var qp = new QApplicant("a");

        var query = configBuilder.create(em, Applicant.class)
                .from(Applicant.class, qp.getMetadata().getName());

        // Filter dipasang HANYA kalau keyword dikirim - inilah alasan Blaze dipakai.
        // Minimal 2 huruf supaya LIKE '%a%' tidak memindai seluruh tabel.
        if (filter.getKeyword() != null && filter.getKeyword().trim().length() >= 2) {
            var kw = "%" + filter.getKeyword().trim().toLowerCase() + "%";

            query.whereOr()
                    .where(LOWER_PREFIX + qp.fullName + ")").like().value(kw).noEscape()
                    .where(LOWER_PREFIX + qp.email + ")").like().value(kw).noEscape()
                    .where(LOWER_PREFIX + qp.phoneNumber + ")").like().value(kw).noEscape()
                    .endOr();
        }

        // page() mewajibkan urutan unik, makanya id yang dipakai.
        query.orderByDesc(qp.id.toString());

        PagedList<ApplicantSummaryView> res = viewManager
                .applySetting(EntityViewSetting.create(ApplicantSummaryView.class, offset, perPage), query)
                .getResultList();

        return ApiResponse.successPaged(
                "Berhasil mengambil data applicant",
                res.stream().toList(),
                page,
                perPage,
                res.getTotalSize()
        );
    }

    private ApplicantResponse toResponse(Applicant a) {
        return new ApplicantResponse(
                a.getId(),
                a.getFullName(),
                a.getNik(),
                a.getDateOfBirth(),
                a.getPhoneNumber(),
                a.getEmail(),
                a.getAddress()
        );
    }

    // SHA-256 satu arah, BUKAN untuk sembunyikan NIK - tujuannya cuma bikin nilai
    // deterministic yang bisa di-index unik di DB tanpa perlu decrypt NIK asli.
    private String hashNik(String nik) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(nik.getBytes(StandardCharsets.UTF_8));

            StringBuilder hex = new StringBuilder();
            for (byte b : hashBytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Algoritma SHA-256 tidak tersedia", e);
        }
    }
}