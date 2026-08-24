package org.project.loslite.service;

import org.project.loslite.service.FileStorage;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Implementasi ASLI dari port FileStorage - simpan file ke disk lokal folder
 * "uploads/documents". Kalau nanti mau pindah ke S3/cloud storage, cukup bikin
 * implementasi baru (mis. S3FileStorage) tanpa DocumentService berubah sama sekali.
 */
@Component
public class LocalFileStorage implements FileStorage {

    private static final String BASE_DIR = "uploads/documents";

    @Override
    public String save(MultipartFile file) {
        try {
            // UUID + nama asli - supaya 2 file dengan nama sama tidak saling menimpa.
            String uniqueFilename = UUID.randomUUID() + "_" + file.getOriginalFilename();
            Path targetPath = Paths.get(BASE_DIR, uniqueFilename);

            Files.createDirectories(targetPath.getParent());
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

            return targetPath.toString();
        } catch (IOException e) {
            // Kegagalan I/O (disk penuh, permission, dst) - bukan kesalahan user,
            // wajar unchecked supaya ketangkap GlobalExceptionHandler.handleGeneric().
            throw new UncheckedIOException("Gagal menyimpan file ke disk", e);
        }
    }
}
