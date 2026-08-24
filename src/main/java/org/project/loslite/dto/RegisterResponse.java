package org.project.loslite.dto;

import org.project.loslite.enums.UserRole;

/**
 * Bentuk JSON balasan setelah register berhasil.
 * Sengaja TIDAK ada field password/passwordHash sama sekali -> tidak pernah
 * boleh ada rahasia (walau sudah di-hash) yang keluar lewat response API.
 */
public record RegisterResponse(
        Long id,
        String username,
        String fullName,
        UserRole role
) {
}
