package org.project.loslite.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.List;

/**
 * SATU-SATUNYA bentuk amplop response HTTP di aplikasi ini — dipakai untuk
 * response sukses MAUPUN gagal, supaya konsumen API (frontend/Postman) selalu
 * tahu persis field apa yang akan ada, tidak perlu logic berbeda tergantung
 * sukses/gagal. Cukup baca field "success" untuk tahu mana yang terjadi.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean success,
        String message,
        T data,
        Integer page,
        Integer perPage,
        Integer maxPage,
        Long totalData,
        List<FieldErrorDetail> validationErrors,
        LocalDateTime timestamp
) {

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, message, data, null, null, null, null, null, LocalDateTime.now());
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, message, null, null, null, null, null, null, LocalDateTime.now());
    }

    public static <T> ApiResponse<T> validationError(String message, List<FieldErrorDetail> validationErrors) {
        return new ApiResponse<>(false, message, null, null, null, null, null, validationErrors, LocalDateTime.now());
    }

    public static <T> ApiResponse<List<T>> successPaged(String message, List<T> data,
                                                        int page, int perPage, long totalData) {
        int maxPage = (perPage <= 0) ? 0 : (int) Math.ceil((double) totalData / perPage);
        return new ApiResponse<>(true, message, data, page, perPage, maxPage, totalData, null, LocalDateTime.now());
    }

    public record FieldErrorDetail(String field, String message) {
    }
}
