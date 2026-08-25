package org.project.loslite.service;

import com.blazebit.persistence.CriteriaBuilderFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.project.loslite.dto.AuthResult;
import org.project.loslite.enums.UserRole;
import org.project.loslite.exception.DuplicateResourceException;
import org.project.loslite.model.AppUser;
import org.project.loslite.model.QAppUser;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use case: login & registrasi staff internal.
 * <p>
 * Query disusun langsung lewat Blaze-Persistence + EntityManager, tanpa repository.
 * Path kolom memakai metamodel QueryDSL (QAppUser) supaya salah ketik nama field
 * ketahuan saat compile, bukan saat endpoint dipanggil.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    @PersistenceContext
    private EntityManager em;

    private final CriteriaBuilderFactory configBuilder;
    private final PasswordEncoder passwordEncoder;
    private final TokenProvider tokenProvider;

    @Transactional(readOnly = true)
    public AuthResult login(String username, String rawPassword) {

        var qp = new QAppUser("u");

        var res = configBuilder.create(em, AppUser.class)
                .from(AppUser.class, qp.getMetadata().getName())
                .where(qp.username.toString()).eq(username)
                .setMaxResults(1)
                .getResultList();

        // Pesan sengaja SAMA untuk "username tidak ada" dan "password salah" -
        // supaya penyerang tidak bisa menebak username mana yang terdaftar.
        if (res.isEmpty()) {
            throw new BadCredentialsException("Username atau password salah");
        }

        var user = res.get(0);

        // passwordEncoder.matches(raw, hash) -> cocokkan password mentah dengan hash
        // di DB. TIDAK PERNAH pakai equals(), karena BCrypt satu arah.
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new BadCredentialsException("Username atau password salah");
        }

        var token = tokenProvider.generateToken(user);

        return new AuthResult(token, user.getUsername(), user.getFullName(), user.getRole());
    }

    @Transactional
    public AppUser register(String username, String rawPassword, String fullName, UserRole role) {

        var qp = new QAppUser("u");

        // Ambil kolom id saja + LIMIT 1 - yang dibutuhkan cuma "ada atau tidak".
        var duplicate = configBuilder.create(em, Long.class)
                .from(AppUser.class, qp.getMetadata().getName())
                .select(qp.id.toString())
                .where(qp.username.toString()).eq(username)
                .setMaxResults(1)
                .getResultList();

        if (!duplicate.isEmpty()) {
            throw new DuplicateResourceException("Username '" + username + "' sudah dipakai");
        }

        var newUser = AppUser.builder()
                .username(username)
                .passwordHash(passwordEncoder.encode(rawPassword)) // encode = hash 1 arah
                .fullName(fullName)
                .role(role)
                .build();

        em.persist(newUser);

        return newUser;
    }
}