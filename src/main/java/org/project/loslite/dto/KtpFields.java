package org.project.loslite.dto;

/**
 * Field terstruktur hasil parsing KTP dari Python OCR service. Nama field
 * sengaja sama persis dengan JSON yang dibalas Python (app/schemas.py::KtpFields)
 * supaya Jackson deserialize otomatis tanpa mapping manual.
 */
public record KtpFields(
        String nama,
        String tempatTanggalLahir,
        String jenisKelamin,
        String golonganDarah,
        String alamat,
        String rtRw,
        String kelurahanDesa,
        String kecamatan,
        String agama,
        String statusPerkawinan,
        String pekerjaan,
        String kewarganegaraan,
        String berlakuHingga
) {
}
