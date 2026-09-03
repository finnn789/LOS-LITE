package org.project.loslite.service;

import lombok.RequiredArgsConstructor;
import org.project.loslite.dto.CreateApplicantCommand;
import org.project.loslite.model.Applicant;
import org.project.loslite.persist.ApplicantPersist;
import org.project.loslite.exception.DuplicateResourceException;
import org.project.loslite.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ApplicantService {

    private final ApplicantPersist applicantPersist;

    @Transactional
    public Applicant create(CreateApplicantCommand command) {
        String nikHash = hashNik(command.nik());

        // Cek duplikat lewat HASH, bukan NIK mentah - konsisten dengan desain akhir
        // nanti (saat NIK sudah dienkripsi, NIK asli tidak bisa di-query langsung
        // di WHERE clause, tapi hash-nya tetap bisa karena deterministic).
        if (applicantPersist.existsByNikHash(nikHash)) {
            throw new DuplicateResourceException("Applicant dengan NIK ini sudah terdaftar");
        }

        // CATATAN (sementara): nik disimpan PLAIN TEXT dulu - enkripsi AES-GCM
        // akan ditambahkan belakangan lewat JPA AttributeConverter, setelah itu
        // baris ini tidak perlu berubah sama sekali (converter bekerja transparan
        // di level JPA, field Java-nya tetap String biasa).
        Applicant applicant = Applicant.builder()
                .fullName(command.fullName())
                .nik(command.nik())
                .nikHash(nikHash)
                .dateOfBirth(command.dateOfBirth())
                .phoneNumber(command.phoneNumber())
                .email(command.email())
                .address(command.address())
                .build();

        return applicantPersist.save(applicant);
    }

    @Transactional(readOnly = true)
    public Applicant getById(Long id) {
        return applicantPersist.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Applicant dengan id " + id + " tidak ditemukan"));
    }

    @Transactional(readOnly = true)
    public List<Applicant> getAll() {
        return applicantPersist.findAll();
    }

    // SHA-256 satu arah, BUKAN untuk sembunyikan NIK (itu tugas enkripsi nanti),
    // tujuannya cuma bikin nilai yang deterministic dan bisa di-index unik di DB
    // tanpa perlu decrypt NIK asli tiap kali cek duplikat.
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
            // SHA-256 SELALU tersedia di JVM manapun (algoritma wajib menurut spec Java) -
            // ini pengecualian yang secara praktik tidak akan pernah kejadian.
            throw new IllegalStateException("Algoritma SHA-256 tidak tersedia", e);
        }
    }
}
