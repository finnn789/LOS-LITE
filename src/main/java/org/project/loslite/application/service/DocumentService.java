package org.project.loslite.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.project.loslite.application.dto.OcrResult;
import org.project.loslite.domain.enums.DocumentType;
import org.project.loslite.domain.enums.OcrStatus;
import org.project.loslite.domain.model.Document;
import org.project.loslite.domain.model.LoanApplication;
import org.project.loslite.domain.repository.DocumentRepository;
import org.project.loslite.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    private static final long MAX_FILE_SIZE_BYTES = 5L * 1024 * 1024; // 5MB
    private static final List<String> ALLOWED_CONTENT_TYPES = List.of("image/jpeg", "image/png");

    private final DocumentRepository documentRepository;
    private final LoanApplicationService loanApplicationService;
    private final FileStorage fileStorage;
    private final OcrClient ocrClient;
    private final ObjectMapper objectMapper;

    @Transactional
    public Document upload(MultipartFile file, Long loanApplicationId, DocumentType documentType) {
        validateFile(file);

        // Pastikan LoanApplication-nya benar ada - kalau tidak, ResourceNotFoundException
        // (dilempar getById) sebelum kita buang-buang waktu simpan file ke disk.
        LoanApplication loanApplication = loanApplicationService.getById(loanApplicationId);

        String storedPath = fileStorage.save(file);

        Document document = Document.builder()
                .loanApplication(loanApplication)
                .documentType(documentType)
                .filePath(storedPath)
                .ocrStatus(OcrStatus.PENDING)
                .build();

        runOcr(document, file, documentType);

        return documentRepository.save(document);
    }

    // OCR dijalankan SINKRON di sini (bagian dari request upload yang sama), tapi
    // kegagalannya SENGAJA tidak dilempar ke atas (tidak throw) - upload dokumen
    // harus tetap berhasil walau Python service down/error. OFFICER masih bisa lanjut
    // kerja, cuma ocrStatus jadi FAILED, bisa di-retry manual nanti (endpoint retry
    // belum dibuat - di luar scope sekarang).
    private void runOcr(Document document, MultipartFile file, DocumentType documentType) {
        try {
            OcrResult result = ocrClient.extract(file, documentType);
            document.setOcrStatus(OcrStatus.PROCESSED);
            document.setOcrRawResult(toJson(result));
        } catch (Exception e) {
            log.warn("OCR gagal untuk document (loanApplicationId={}): {}",
                    document.getLoanApplication().getId(), e.getMessage());
            document.setOcrStatus(OcrStatus.FAILED);
        }
    }

    private String toJson(OcrResult result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Gagal serialize OcrResult ke JSON", e);
        }
    }

    @Transactional(readOnly = true)
    public Document getById(Long id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Document dengan id " + id + " tidak ditemukan"));
    }

    @Transactional(readOnly = true)
    public List<Document> getByLoanApplicationId(Long loanApplicationId) {
        return documentRepository.findByLoanApplicationId(loanApplicationId);
    }

    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File tidak boleh kosong");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException("Ukuran file maksimal 5MB");
        }
        if (!ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
            throw new IllegalArgumentException("Tipe file harus JPEG atau PNG");
        }
    }
}
