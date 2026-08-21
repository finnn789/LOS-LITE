package org.project.loslite.application.dto;

import org.project.loslite.domain.enums.UserRole;

/**
 * Hasil dari use case login, dipakai INTERNAL antara application dan interfaces layer.
 * <p>
 * Sengaja dibuat terpisah dari DTO response HTTP (yang nanti dibuat di interfaces/dto).
 * Alasannya: application layer tidak boleh tahu bentuk JSON response API —
 * itu urusan interfaces layer. Record ini murni membawa "hasil keputusan"
 * dari use case, apa pun cara pemanggilnya nanti (REST, atau misal CLI/test).
 */
public record AuthResult(
        String token,
        String username,
        String fullName,
        UserRole role
) {
}
