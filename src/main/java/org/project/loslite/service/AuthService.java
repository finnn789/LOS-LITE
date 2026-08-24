package org.project.loslite.service;

import lombok.RequiredArgsConstructor;
import org.project.loslite.dto.AuthResult;
import org.project.loslite.enums.UserRole;
import org.project.loslite.model.AppUser;
import org.project.loslite.repository.AppUserRepository;
import org.project.loslite.exception.DuplicateResourceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use case: login staff internal (OFFICER/ADMIN).
 * <p>
 * Ini "Service" di application layer — isinya ORKESTRASI, bukan aturan bisnis kompleks
 * dan bukan detail teknis. Tugasnya cuma 3: ambil data (lewat domain repository),
 * ambil keputusan sederhana (password cocok atau tidak), delegasikan hal teknis
 * (bikin token) ke port TokenProvider.
 * <p>
 * @Service menandai kelas ini sebagai Spring bean biasa (bukan @Entity/@Repository/@Controller) —
 * Spring akan otomatis suntikkan (inject) AuthService ini ke controller yang membutuhkannya.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenProvider tokenProvider;

    public AuthResult login(String username, String rawPassword) {

        AppUser user = appUserRepository.findByUsername(username)
                .orElseThrow(() -> new BadCredentialsException("Username atau password salah"));

        // passwordEncoder.matches(raw, hash) -> cocokkan password mentah dari user
        // dengan hash yang tersimpan di DB. TIDAK PERNAH bandingkan String secara langsung (==/equals),
        // karena passwordHash di DB sudah di-hash satu arah (BCrypt), tidak bisa "dibalikin".
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new BadCredentialsException("Username atau password salah");
        }

        String token = tokenProvider.generateToken(user);

        return new AuthResult(token, user.getUsername(), user.getFullName(), user.getRole());
    }

    /**
     * Use case: daftarkan akun staff baru (OFFICER/ADMIN).
     * <p>
     * @Transactional di sini menandai "batas transaksi" use case ini — kalau ada
     * error SETELAH save() (belum ada sekarang, tapi misalnya nanti kirim notifikasi
     * yang gagal), seluruh operasi termasuk save() ikut di-rollback. Untuk method
     * login() di atas tidak perlu @Transactional karena dia cuma baca data (read-only),
     * tidak ada perubahan state yang perlu "batal semua kalau salah satu gagal".
     */
    @Transactional
    public AppUser register(String username, String rawPassword, String fullName,UserRole role) {

        if (appUserRepository.existsByUsername(username)) {
            throw new DuplicateResourceException("Username '" + username + "' sudah dipakai");
        }

        AppUser newUser = AppUser.builder()
                .username(username)
                .passwordHash(passwordEncoder.encode(rawPassword)) // encode = hash 1 arah, BUKAN dekripsi-able
                .fullName(fullName)
                .role(role)
                .build();

        return appUserRepository.save(newUser);
    }


}
