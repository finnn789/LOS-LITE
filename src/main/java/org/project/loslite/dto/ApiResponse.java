package org.project.loslite.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * SATU-SATUNYA bentuk amplop response HTTP di aplikasi ini — dipakai untuk
 * response sukses MAUPUN gagal, supaya konsumen API (frontend/Postman) selalu
 * tahu persis field apa yang akan ada, tidak perlu logic berbeda tergantung
 * sukses/gagal. Cukup baca field "success" untuk tahu mana yang terjadi.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private boolean success;
    private String message;
    private T data;
    private List<FieldErrorDetail> validationErrors;
    private LocalDateTime timestamp;

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, message, data, null, LocalDateTime.now());
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, message, null, null, LocalDateTime.now());
    }

    public static <T> ApiResponse<T> validationError(String message, List<FieldErrorDetail> validationErrors) {
        return new ApiResponse<>(false, message, null, validationErrors, LocalDateTime.now());
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FieldErrorDetail {
        private String field;
        private String message;
    }
}
