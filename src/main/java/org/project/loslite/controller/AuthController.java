package org.project.loslite.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.project.loslite.dto.AuthResult;
import org.project.loslite.service.AuthService;
import org.project.loslite.enums.UserRole;
import org.project.loslite.model.AppUser;
import org.project.loslite.dto.ApiResponse;
import org.project.loslite.dto.LoginRequest;
import org.project.loslite.dto.LoginResponse;
import org.project.loslite.dto.RegisterRequest;
import org.project.loslite.dto.RegisterResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

/**
 * Pintu masuk HTTP untuk auth. Tugas controller HANYA 3:
 * terima request -> panggil use case (AuthService) -> bungkus hasilnya jadi response HTTP.
 * TIDAK ADA logika bisnis atau teknis di sini sama sekali.
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {

        // @Valid -> Spring validasi LoginRequest dulu (cek @NotBlank) SEBELUM baris ini jalan.
        // Kalau tidak valid, method ini tidak pernah dieksekusi -> langsung ke GlobalExceptionHandler.

        AuthResult result = authService.login(request.username(), request.password());

        // Mapping manual dari AuthResult (application) ke LoginResponse (interfaces/HTTP).
        // Untuk sekarang isinya sama persis, tapi keduanya tetap 2 class yang beda tujuan.
        LoginResponse response = new LoginResponse(
                result.token(),
                result.username(),
                result.fullName(),
                result.role()
        );

        return ResponseEntity.ok(ApiResponse.success("Login berhasil", response));
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<RegisterResponse>> register(@Valid @RequestBody RegisterRequest request) {

        AppUser newUser = authService.register(
                request.username(),
                request.password(),
                request.fullName(),
                request.role()
        );

        RegisterResponse response = new RegisterResponse(
                newUser.getId(),
                newUser.getUsername(),
                newUser.getFullName(),
                newUser.getRole()
        );

        // 201 CREATED, bukan 200 OK -> konvensi HTTP untuk "resource baru berhasil dibuat"
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Registrasi berhasil", response));
    }

    @GetMapping("/get-role")
    public ResponseEntity<ApiResponse<List<UserRole>>> getUserRole() {
        var userRoleEnum = Arrays.stream(UserRole.values()).toList();
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success("Berhasil Mengambil Role", userRoleEnum));
    }
}
