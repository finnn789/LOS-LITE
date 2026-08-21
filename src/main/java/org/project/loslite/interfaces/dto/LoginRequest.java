package org.project.loslite.interfaces.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Bentuk body JSON yang diterima dari client di POST /auth/login.
 * @Valid di controller yang akan memicu validasi @NotBlank ini,
 * ditangkap otomatis oleh GlobalExceptionHandler (MethodArgumentNotValidException -> 400)
 * kalau username/password kosong.
 */
public record LoginRequest(

        @NotBlank(message = "Username wajib diisi")
        String username,

        @NotBlank(message = "Password wajib diisi")
        String password
) {
}
