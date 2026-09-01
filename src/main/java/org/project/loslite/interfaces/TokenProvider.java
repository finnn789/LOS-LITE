package org.project.loslite.interfaces;

import org.project.loslite.model.AppUser;

/**
 * Kontrak (port) untuk pembuatan access token.
 * <p>
 * AuthService HANYA kenal interface ini, tidak kenal JWT/library apa pun.
 * Implementasi asli (pakai library jjwt, baca secret key, atur expiry, dst)
 * ada di infrastructure layer — itu detail teknis yang boleh berubah/diganti
 * tanpa menyentuh application layer sama sekali. Ini prinsip Dependency Inversion:
 * application (high-level) mendefinisikan apa yang ia butuhkan,
 * infrastructure (low-level) yang menyesuaikan diri.
 */
public interface TokenProvider {

    /**
     * Buat access token untuk user yang berhasil login.
     */
    String generateToken(AppUser user);

    /**
     * Cek apakah token masih valid: tanda tangan cocok DAN belum expired.
     * Dipakai JwtAuthenticationFilter sebelum mempercayai isi token.
     */
    boolean validateToken(String token);

    /**
     * Ambil username (subject) dari token yang SUDAH divalidasi.
     */
    String extractUsername(String token);

    /**
     * Ambil role dari token yang SUDAH divalidasi - dipakai untuk otorisasi
     * (menentukan authority apa yang dipunya user ini di request tersebut).
     */
    String extractRole(String token);
}
