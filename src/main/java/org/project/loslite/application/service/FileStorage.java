package org.project.loslite.application.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * Kontrak (port) untuk menyimpan file upload. DocumentService HANYA kenal
 * interface ini, tidak tahu apakah file disimpan di disk lokal, S3, dsb -
 * sama prinsipnya dengan TokenProvider (Dependency Inversion).
 */
public interface FileStorage {

    /**
     * Simpan file, kembalikan path/lokasi yang bisa dipakai untuk mengambilnya lagi nanti.
     */
    String save(MultipartFile file);
}
