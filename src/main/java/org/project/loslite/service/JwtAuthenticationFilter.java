package org.project.loslite.service;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * "Penjaga pintu" yang jalan SEKALI untuk setiap request, SEBELUM request sampai
 * ke controller. Tugasnya cuma 3:
 * 1. Ambil token JWT dari header Authorization (kalau ada)
 * 2. Validasi token itu (lewat TokenProvider - kita tidak tahu/peduli ini JWT beneran)
 * 3. Kalau valid, isi SecurityContextHolder supaya Spring Security tahu
 * "request ini datang dari user yang sudah terautentikasi"
 * <p>
 * extends OncePerRequestFilter (bukan langsung implements Filter) supaya Spring
 * jamin filter ini TIDAK jalan dobel untuk 1 request yang sama (bisa terjadi
 * kalau ada internal forward di servlet container).
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final TokenProvider tokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        String token = extractTokenFromHeader(request);
//        System.out.println(token);
        // Tidak ada token, atau tidak valid -> JANGAN tolak di sini (jangan return 401
        // manual). Cukup biarkan request lewat TANPA mengisi SecurityContext.
        // Keputusan "ditolak atau tidak" tetap jadi tanggung jawab authorizeHttpRequests
        // di SecurityConfig - filter ini cuma bertugas MENGISI identitas kalau ada,
        // bukan menjaga endpoint mana yang butuh login.
        if (token != null && tokenProvider.validateToken(token)) {
            String username = tokenProvider.extractUsername(token);
            String role = tokenProvider.extractRole(token);



            // Spring Security butuh authority dalam format "ROLE_xxx" (konvensi bawaan)
            // supaya nanti bisa dipakai .hasRole("ADMIN") di SecurityConfig kalau perlu.
            List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));

            var authentication = new UsernamePasswordAuthenticationToken(username,       // principal - "siapa" nya
                    null,           // credentials - dikosongkan, kita tidak simpan password di sini
                    authorities     // authorities - "boleh ngapain aja" nya
            );

            // INI baris paling penting di seluruh filter ini - baru setelah baris ini
            // jalan, Spring Security menganggap request ini "authenticated".
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        // WAJIB dipanggil, apa pun hasilnya di atas - supaya request diteruskan
        // ke filter berikutnya dalam chain (dan akhirnya ke controller).
        // Lupa panggil ini = request "macet" di sini, tidak akan pernah sampai controller.
        filterChain.doFilter(request, response);
    }

    private String extractTokenFromHeader(HttpServletRequest request) {
        String headerValue = request.getHeader(AUTH_HEADER);

        if (headerValue == null || !headerValue.startsWith(BEARER_PREFIX)) {
            return null;
        }

        return headerValue.substring(BEARER_PREFIX.length());
    }
}
