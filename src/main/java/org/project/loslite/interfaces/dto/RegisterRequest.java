package org.project.loslite.interfaces.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.aspectj.weaver.ast.Not;
import org.project.loslite.domain.enums.UserRole;

/**
 * Bentuk body JSON untuk POST /auth/register.
 * <p>
 * CATATAN KEAMANAN (sengaja ditulis eksplisit): endpoint ini untuk sekarang
 * publik (lihat SecurityConfig -> /auth/** permitAll), termasuk boleh pilih
 * role ADMIN sendiri. Itu wajar untuk tahap bootstrap (belum ada admin sama
 * sekali untuk membuat admin pertama), TAPI di sistem production sungguhan
 * endpoint pembuatan akun staff biasanya wajib login sebagai ADMIN dulu.
 * Ditulis sebagai catatan, bukan diimplementasikan sekarang - di luar scope 5 hari.
 */
public record RegisterRequest(

        @NotBlank(message = "Username wajib diisi")
        @Size(min = 4, max = 50, message = "Username minimal 4 karakter")
        String username,

        @NotBlank(message = "Password wajib diisi")
        @Size(min = 8, message = "Password minimal 8 karakter")
        String password,

        @NotBlank(message = "Nama lengkap wajib diisi")
        String fullName,

        @NotNull(message = "Role Wajib diisi")
        UserRole role
) {
}
