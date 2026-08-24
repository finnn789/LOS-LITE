package org.project.loslite.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.project.loslite.model.AppUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Implementasi ASLI dari port TokenProvider (interface di application layer),
 * pakai library jjwt. Inilah "adapter" — bagian yang tahu detail teknis JWT
 * (signing key, klaim, expiry) yang sengaja disembunyikan dari AuthService.
 * <p>
 * Dipakai @Component (bukan @Service) karena secara DDD ini bukan "use case",
 * ini cuma alat/adapter teknis yang dipasang ke port.
 */
@Component
public class JwtTokenProvider implements TokenProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);

    private final SecretKey signingKey;
    private final long expirationMs;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration-ms}") long expirationMs) {
        // Secret dari application.yml (yang ambil dari .env) diubah jadi SecretKey
        // yang valid untuk algoritma HMAC-SHA. Kalau secret-nya kurang dari 256 bit,
        // baris ini yang akan lempar error saat startup — itu SENGAJA (fail-fast),
        // daripada baru ketahuan lemah pas sudah di production.
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    @Override
    public String generateToken(AppUser user) {
        Date issuedAt = new Date();
        Date expiresAt = new Date(issuedAt.getTime() + expirationMs);

        return Jwts.builder()
                .subject(user.getUsername())          // identitas utama token
                .claim("role", user.getRole().name())  // dipakai nanti oleh filter utk cek otorisasi
                .claim("userId", user.getId())
                .issuedAt(issuedAt)
                .expiration(expiresAt)
                .signWith(signingKey)                  // tanda tangan -> token tidak bisa diubah diam-diam
                .compact();                            // hasil akhir: String JWT (header.payload.signature)
    }

    @Override
    public boolean validateToken(String token) {
        try {
            // parseSignedClaims MELAKUKAN keduanya sekaligus: cek tanda tangan cocok
            // dengan signingKey kita (bukti token ini benar dari server kita, bukan
            // dipalsukan), DAN cek klaim "exp" belum lewat. Kalau salah satu gagal,
            // method ini melempar exception (BUKAN return false) - makanya dibungkus try-catch.
            Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            // Token rusak/kadaluarsa/tanda tangan tidak cocok - BUKAN bug aplikasi,
            // ini kondisi normal (user kirim token basi/invalid). Log level warn,
            // bukan error, dan TIDAK dilempar ulang - cukup bilang "tidak valid".
            log.warn("Validasi JWT gagal: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public String extractUsername(String token) {
        return extractClaims(token).getSubject();
    }

    @Override
    public String extractRole(String token) {
        return extractClaims(token).get("role", String.class);
    }

    private Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
