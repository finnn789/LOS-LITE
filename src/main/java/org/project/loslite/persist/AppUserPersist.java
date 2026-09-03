package org.project.loslite.persist;

import com.blazebit.persistence.CriteriaBuilder;
import com.blazebit.persistence.CriteriaBuilderFactory;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.project.loslite.model.AppUser;
import org.project.loslite.model.QAppUser;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Satu-satunya kelas akses data {@link AppUser}. SENGAJA class biasa, BUKAN interface
 * {@code extends JpaRepository} - lihat catatan pola yang sama di {@code ApplicantRepository}:
 * QueryDSL ({@link QAppUser}) cuma sebagai generator nama
 * path type-safe, mesin eksekusi query tetap Blaze-Persistence {@link CriteriaBuilder}.
 * <p>
 * Method di sini sengaja tidak {@code @Transactional} - transaksi jadi tanggung jawab
 * Service pemanggil ({@code AuthService}).
 */
@Repository
@RequiredArgsConstructor
public class AppUserPersist {

    private static final QAppUser qAppUser = new QAppUser("au");

    private final CriteriaBuilderFactory criteriaBuilderFactory;
    private final EntityManager entityManager;

    public Optional<AppUser> findByUsername(String username) {
        CriteriaBuilder<AppUser> cb = criteriaBuilderFactory
                .create(entityManager, AppUser.class, "au")
                .where(qAppUser.username.toString()).eq(username);

        return cb.getResultList().stream().findFirst();
    }

    public boolean existsByUsername(String username) {
        CriteriaBuilder<AppUser> cb = criteriaBuilderFactory
                .create(entityManager, AppUser.class, "au")
                .where(qAppUser.username.toString()).eq(username);

        return cb.getCountQuery().getSingleResult() > 0;
    }

    /**
     * Insert kalau {@code appUser.getId() == null}, update (merge) kalau sudah ada id.
     * Harus dipanggil dalam konteks transaksi ({@code @Transactional} di Service pemanggil).
     */
    public AppUser save(AppUser appUser) {
        if (appUser.getId() == null) {
            entityManager.persist(appUser);
            return appUser;
        }
        return entityManager.merge(appUser);
    }
}
