package org.project.loslite.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.project.loslite.dto.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Dipanggil Spring Security saat request SUDAH terautentikasi (token valid)
 * tapi tidak punya wewenang untuk resource yang diakses (mis. role salah).
 * <p>
 * Sama seperti {@link RestAuthenticationEntryPoint}, ini dipanggil dari
 * FilterChain sebelum DispatcherServlet, jadi GlobalExceptionHandler tidak
 * pernah kepanggil untuk kasus ini - tanpa bean ini, response 403 bakal
 * kosong tanpa body.
 */
@Component
@RequiredArgsConstructor
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                        AccessDeniedException accessDeniedException) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        var body = ApiResponse.<Void>error("Anda tidak punya akses untuk resource ini");
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
