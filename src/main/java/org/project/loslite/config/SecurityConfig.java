package org.project.loslite.config;

import lombok.RequiredArgsConstructor;
import org.project.loslite.component.AutoLayoutServiceTokenFilter;
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
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

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
    private final AutoLayoutServiceTokenFilter autoLayoutServiceTokenFilter;
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

    /**
     * CORS - sebelum ini LOS-LITE TIDAK PUNYA config CORS sama sekali, jadi SEMUA
     * request lintas-origin dari browser (termasuk preflight OPTIONS) ditolak
     * Spring Security duluan sebelum sampai controller manapun - kejadian nyata:
     * static/forms/dynamic-form.html (form hasil generate Auto Layout, mis. form
     * "auth-login" yang submit ke {@code POST /auth/login}) selalu gagal walau
     * endpoint tujuannya sendiri sudah PUBLIC_PATHS, karena preflight-nya sendiri
     * kena block sebelum authorization check apapun jalan.
     * <p>
     * allowedOriginPatterns("*") + allowCredentials(false) dipilih karena auth
     * LOS-LITE berbasis JWT lewat header Authorization (bukan cookie/session), jadi
     * tidak butuh credentialed CORS - origin manapun (termasuk file://, halaman
     * statis dynamic-form.html yang dibuka lepas dari server ini) boleh manggil,
     * TANPA browser perlu ikut kirim cookie. Kalau nanti butuh dibatasi ke origin
     * tertentu saja (bukan open ke semua), ganti allowedOriginPatterns ke daftar
     * eksplisit.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)

                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .requestMatchers("/api/los/endpoints/**").hasAuthority("ROLE_SCHEMA_READ")
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
        http.addFilterBefore(autoLayoutServiceTokenFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
