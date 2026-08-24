package org.project.loslite.service;

import lombok.RequiredArgsConstructor;
import org.project.loslite.dto.OcrResult;
import org.project.loslite.service.OcrClient;
import org.project.loslite.enums.DocumentType;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Implementasi ASLI dari port OcrClient - panggil service Python (FastAPI) lewat
 * HTTP pakai WebClient. Detail teknis (multipart, base URL, blocking call) hidup
 * SEPENUHNYA di sini, DocumentService tidak tahu-menahu ini HTTP sama sekali.
 */
@Component
@RequiredArgsConstructor
public class WebClientOcrClient implements OcrClient {

    private final WebClient ocrWebClient;

    @Override
    public OcrResult extract(MultipartFile file, DocumentType documentType) {
        MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();

        try {
            // .resource(...) butuh Resource yang tahu nama file aslinya (dipakai Python
            // untuk baca Content-Disposition di multipart request) - ByteArrayResource
            // biasa tidak bawa nama file, jadi kita override getFilename()-nya.
            bodyBuilder.part("file", new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            }).contentType(MediaType.parseMediaType(file.getContentType()));

            // Field form biasa (bukan file) - Python baca ini lewat Form(...) di FastAPI,
            // dipakai untuk pilih parser yang sesuai (KTP vs SLIP_GAJI).
            bodyBuilder.part("document_type", documentType.name());
        } catch (IOException e) {
            throw new UncheckedIOException("Gagal membaca isi file untuk dikirim ke OCR service", e);
        }
        // baseUrl sudah diatur di WebClientConfig (ocr.service.base-url) - di sini
        // cukup tulis path relatifnya saja: "/ocr/extract".
        return ocrWebClient.post().uri("/ocr/extract").contentType(MediaType.MULTIPART_FORM_DATA).body(BodyInserters.fromMultipartData(bodyBuilder.build())).retrieve().bodyToMono(OcrResult.class).block(); // sinkron - DocumentService menunggu hasil OCR selesai
    }
}
