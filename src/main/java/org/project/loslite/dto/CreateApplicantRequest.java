package org.project.loslite.dto;

import jakarta.validation.constraints.*;

import java.time.LocalDate;

public record CreateApplicantRequest(

        @NotBlank(message = "Nama lengkap wajib diisi")
        String fullName,

        @NotBlank(message = "NIK wajib diisi")
        @Pattern(regexp = "\\d{16}", message = "NIK harus terdiri dari 16 digit angka")
        String nik,

        @NotNull(message = "Tanggal lahir wajib diisi")
        @Past(message = "Tanggal lahir harus di masa lalu")
        LocalDate dateOfBirth,

        @NotBlank(message = "Nomor telepon wajib diisi")
        @Size(max = 20, message = "Nomor telepon maksimal 20 karakter")
        String phoneNumber,

        @Email(message = "Format email tidak valid")
        String email,

        String address
) {
}
