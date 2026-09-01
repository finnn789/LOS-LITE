package org.project.loslite.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.project.loslite.dto.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Dipanggil Spring Security saat request TIDAK terautentikasi (belum login /
 * token kosong / token invalid) tapi mencoba akses endpoint yang wajib login.
 * <p>
 * Ini terjadi di FilterChain, SEBELUM request sampai ke DispatcherServlet -
 * jadi {@code GlobalExceptionHandler} (RestControllerAdvice) tidak pernah
 * kepanggil untuk kasus ini. Tanpa bean ini, Spring Security jatuh ke default
 * Http403ForbiddenEntryPoint yang cuma sendError() tanpa body. Di sini kita
 * balikin body JSON yang formatnya konsisten dengan {@link ApiResponse}.
 */
@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                          AuthenticationException authException) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        var body = ApiResponse.<Void>error("Token Tidak Valid");
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
