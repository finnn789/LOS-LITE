package org.project.loslite.config;

import lombok.RequiredArgsConstructor;
import org.project.loslite.component.JwtAuthenticationFilter;
import org.project.loslite.component.RestAccessDeniedHandler;
import org.project.loslite.component.RestAuthenticationEntryPoint;
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
    private final RestAuthenticationEntryPoint restAuthenticationEntryPoint;
    private final RestAccessDeniedHandler restAccessDeniedHandler;

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
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)

                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .anyRequest().authenticated()
                )
                // Penolakan dari FilterChain (belum login / role salah) terjadi SEBELUM
                // DispatcherServlet, jadi GlobalExceptionHandler tidak kepanggil untuk ini -
                // makanya perlu didaftarkan manual di sini, bukan lewat @ExceptionHandler.
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(restAuthenticationEntryPoint)
                        .accessDeniedHandler(restAccessDeniedHandler)
                );
        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
