package org.project.loslite.component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Penjaga pintu KHUSUS buat Auto-Layout, terpisah dari {@link JwtAuthenticationFilter}
 * (yang buat sesi login user biasa). Verifikasi header X-Service-Token pakai secret
 * SENDIRI ({@code autolayout.service.jwt-secret}, bukan {@code jwt.secret} yang dipakai
 * buat token login user) - kalau salah satu secret bocor, gak otomatis bisa dipakai
 * buat forge yang lain.
 * <p>
 * Beda dari ServiceTokenAuthenticationFilter versi lama yang cuma bandingin string
 * (.equals()) - di sini token beneran di-parse & diverifikasi tanda tangan + expiry-nya,
 * karena token generate-nya emang JWT dengan claim exp yang harus benar-benar berlaku.
 */
@Component
public class AutoLayoutServiceTokenFilter extends OncePerRequestFilter {

    private static final String HEADER = "X-Service-Token";

    private final SecretKey key;

    public AutoLayoutServiceTokenFilter(@Value("${autolayout.service.jwt-secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = request.getHeader(HEADER);

        if (token != null) {
            try {
                Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();

                if (claims.getAudience().contains("los-lite")) {
                    List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_SCHEMA_READ"));
                    var authentication = new UsernamePasswordAuthenticationToken(claims.getSubject(), null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (JwtException ignored) {
                // token invalid/kadaluarsa -> biarkan lewat sebagai anonymous, ditolak di authorizeHttpRequests
            }
        }

        chain.doFilter(request, response);
    }
}
