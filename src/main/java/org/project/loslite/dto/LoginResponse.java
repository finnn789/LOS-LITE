package org.project.loslite.dto;

import org.project.loslite.enums.UserRole;

/**
 * Bentuk JSON yang dikirim balik ke client setelah login berhasil.
 * <p>
 * Sengaja dibuat TERPISAH dari AuthResult (application layer) walau isinya
 * mirip sekarang — supaya kalau nanti bentuk response HTTP perlu beda dari
 * hasil use case (mis. tambah field "expiresIn", atau sembunyikan "role"),
 * yang berubah cukup di sini, AuthService tidak perlu disentuh sama sekali.
 */
public record LoginResponse(
        String token,
        String username,
        String fullName,
        UserRole role
) {
}
