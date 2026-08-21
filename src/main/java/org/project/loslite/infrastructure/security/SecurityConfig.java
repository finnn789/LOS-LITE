package org.project.loslite.infrastructure.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Bean-bean teknis seputar security. Ini murni "colokan" (infrastructure),
 * BUKAN tempat aturan bisnis siapa boleh akses apa — di sini cuma diatur
 * endpoint mana yang publik vs yang wajib login, bukan aturan bisnis loan.
 */
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    // Path yang boleh diakses TANPA token. Selain ini, semua wajib bawa JWT valid.
    private static final String[] PUBLIC_PATHS = {
            "/auth/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**"
    };

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    /**
     * BCrypt: algoritma hashing satu-arah + otomatis pakai salt acak per password,
     * jadi 2 user dengan password sama tetap punya passwordHash yang beda di DB.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // CSRF itu proteksi khusus untuk sesi berbasis cookie/browser form.
                // Kita stateless + kirim JWT lewat header Authorization -> tidak relevan, aman dimatikan.
                .csrf(AbstractHttpConfigurer::disable)
                
                // login kita sendiri lewat POST /auth/login yang mengembalikan JWT.
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                // STATELESS: server TIDAK menyimpan session user di memori/cookie.
                // Setiap request wajib bawa bukti identitasnya sendiri (JWT), tidak mengandalkan session.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .anyRequest().authenticated()
                );

        // Sisipkan JwtAuthenticationFilter SEBELUM UsernamePasswordAuthenticationFilter
        // (filter bawaan Spring Security untuk login form, yang kita disable tapi tetap
        // ada di chain). Urutan ini penting: SecurityContext harus SUDAH terisi oleh
        // filter kita SEBELUM filter-filter berikutnya (termasuk authorizeHttpRequests
        // di atas) mengecek "apakah user ini authenticated?".
        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
