package org.project.loslite.service;

import org.project.loslite.dto.OcrResult;
import org.project.loslite.enums.DocumentType;
import org.springframework.web.multipart.MultipartFile;

/**
 * Kontrak (port) untuk memanggil layanan OCR eksternal. DocumentService HANYA
 * kenal interface ini - tidak tahu itu Python/FastAPI/HTTP/gRPC/apa pun.
 * Prinsipnya sama persis dengan TokenProvider dan FileStorage: application
 * layer mendefinisikan APA yang ia butuhkan, infrastructure yang menyediakan
 * BAGAIMANA caranya.
 */
public interface OcrClient {

    /**
     * Kirim file ke layanan OCR, tunggu hasil ekstraksinya (panggilan sinkron/blocking).
     * documentType dikirim juga - parser di sisi OCR service beda untuk KTP vs SLIP_GAJI.
     */
    OcrResult extract(MultipartFile file, DocumentType documentType);
}
