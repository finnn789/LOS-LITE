package org.project.loslite.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.project.loslite.dto.OcrResult;
import org.project.loslite.service.DocumentService;
import org.project.loslite.enums.DocumentType;
import org.project.loslite.model.Document;
import org.project.loslite.dto.ApiResponse;
import org.project.loslite.dto.DocumentResponse;
import org.project.loslite.dto.OcrResultResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Upload dan lihat dokumen (KTP, slip gaji) untuk satu LoanApplication.
 * OCR (baca isi dokumen) BELUM jalan di sini - ocrStatus selalu PENDING dulu
 * setelah upload, diproses fase berikutnya lewat integrasi Python.
 */
@RestController
@RequestMapping
@RequiredArgsConstructor
public class DocumentController {

    private static final Logger log = LoggerFactory.getLogger(DocumentController.class);

    private final DocumentService documentService;
    private final ObjectMapper objectMapper;

    @PostMapping(value = "/documents", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<DocumentResponse>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("loanApplicationId") Long loanApplicationId,
            @RequestParam("documentType") DocumentType documentType) {

        Document uploaded = documentService.upload(file, loanApplicationId, documentType);

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Dokumen berhasil diupload", toResponse(uploaded)));
    }

    @GetMapping("/documents/{id}")
    public ResponseEntity<ApiResponse<DocumentResponse>> getById(@PathVariable Long id) {
        Document document = documentService.getById(id);
        return ResponseEntity.ok(ApiResponse.success("Berhasil mengambil data dokumen", toResponse(document)));
    }

    @GetMapping("/loan-applications/{loanApplicationId}/documents")
    public ResponseEntity<ApiResponse<List<DocumentResponse>>> getByLoanApplication(
            @PathVariable Long loanApplicationId) {

        List<DocumentResponse> responses = documentService.getByLoanApplicationId(loanApplicationId).stream()
                .map(this::toResponse)
                .toList();

        return ResponseEntity.ok(ApiResponse.success("Berhasil mengambil daftar dokumen", responses));
    }

    private DocumentResponse toResponse(Document entity) {
        return new DocumentResponse(
                entity.getId(),
                entity.getLoanApplication().getId(),
                entity.getDocumentType(),
                entity.getFilePath(),
                entity.getOcrStatus(),
                parseOcrResult(entity.getOcrRawResult()),
                entity.getUploadedAt()
        );
    }

    // ocrRawResult di database itu String JSON MENTAH (hasil objectMapper.writeValueAsString
    // di DocumentService) - di sini kita baca BALIK jadi objek Java, lalu petakan ke bentuk
    // response yang lebih enak dibaca (rawText dipecah per baris jadi List<String>).
    private OcrResultResponse parseOcrResult(String ocrRawResultJson) {
        if (ocrRawResultJson == null) {
            return null; // OCR belum jalan (PENDING) atau gagal (FAILED) - memang tidak ada hasil
        }

        try {
            OcrResult result = objectMapper.readValue(ocrRawResultJson, OcrResult.class);
            return new OcrResultResponse(result.nik(), result.estimatedSalary(), result.ktp());
        } catch (JsonProcessingException e) {
            // Data di database rusak/tidak sesuai format yang diharapkan - jangan sampai
            // bikin SELURUH response gagal (500) cuma karena satu field ini bermasalah,
            // cukup log dan kembalikan null untuk field ini saja.
            log.warn("Gagal parse ocrRawResult untuk document id={}: {}", ocrRawResultJson, e.getMessage());
            return null;
        }
    }
}
