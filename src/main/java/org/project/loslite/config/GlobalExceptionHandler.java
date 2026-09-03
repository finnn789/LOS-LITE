package org.project.loslite.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.project.loslite.dto.ApiResponse;
import org.project.loslite.exception.DuplicateResourceException;
import org.project.loslite.exception.InvalidLoanStatusTransitionException;
import org.project.loslite.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // --- Body request tidak lolos validasi @Valid (mis. @NotBlank kosong) ---
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        var fieldErrors = ex.getBindingResult().getFieldErrors().stream().map(fe -> new ApiResponse.FieldErrorDetail(fe.getField(), fe.getDefaultMessage())).collect(Collectors.toList());

        var body = ApiResponse.<Void>validationError("Satu atau lebih field tidak valid", fieldErrors);
        return ResponseEntity.badRequest().body(body);
    }

    // --- Validasi path/query param (@Validated) ---
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        var body = ApiResponse.<Void>error(ex.getMessage());
        return ResponseEntity.badRequest().body(body);
    }

    // --- Body JSON malformed / tidak bisa di-parse ---
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        var body = ApiResponse.<Void>error("Request body tidak valid atau tidak bisa dibaca");
        return ResponseEntity.badRequest().body(body);
    }

    // --- Data tidak ditemukan (dilempar Spring Data, mis. Optional.orElseThrow bawaan) ---
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(NoSuchElementException ex, HttpServletRequest request) {
        var body = ApiResponse.<Void>error(ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    // --- Resource custom kita sendiri tidak ketemu (dilempar manual di application layer) ---
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        var body = ApiResponse.<Void>error(ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    // --- Duplikat data yang kita cek sendiri di application layer (mis. username sudah dipakai) ---
    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicateResource(DuplicateResourceException ex, HttpServletRequest request) {
        var body = ApiResponse.<Void>error(ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    // --- Validasi manual di service (bukan lewat @Valid), mis. cek ukuran/tipe file upload ---
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        var body = ApiResponse.<Void>error(ex.getMessage());
        return ResponseEntity.badRequest().body(body);
    }

    // --- Percobaan pindah LoanStatus yang melanggar state machine (mis. DRAFT -> DISBURSED) ---
    @ExceptionHandler(InvalidLoanStatusTransitionException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidStatusTransition(InvalidLoanStatusTransitionException ex, HttpServletRequest request) {
        var body = ApiResponse.<Void>error(ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    // --- Pelanggaran constraint DB (misal unique key) ---
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        var body = ApiResponse.<Void>error("Data melanggar constraint (kemungkinan duplikat)");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    // --- Autentikasi & otorisasi (Spring Security) ---
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        var body = ApiResponse.<Void>error(ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        var body = ApiResponse.<Void>error("Anda tidak punya akses untuk resource ini");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }


//    @ExceptionHandler(DomainException.class)
//    public ResponseEntity<ApiResponse<Void>> handleDomainException(DomainException ex,
//                                                                     HttpServletRequest request) {
//        var body = ApiResponse.<Void>error(ex.getMessage());
//        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(body);
//    }


    // --- Fallback terakhir: apa pun yang tidak tertangkap di atas ---
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex, HttpServletRequest request) {
        // Exception aslinya tercatat ke log di sini, response ke client tetap pesan
        // generik (tidak bocorkan detail internal aplikasi ke luar).
        log.error("Unhandled exception at {}", request.getRequestURI(), ex);

        var body = ApiResponse.<Void>error("Terjadi kesalahan yang tidak terduga");
        return ResponseEntity.internalServerError().body(body);
    }
}
