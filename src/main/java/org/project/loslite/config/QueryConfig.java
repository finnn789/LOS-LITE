package org.project.loslite.config;

import com.blazebit.persistence.Criteria;
import com.blazebit.persistence.CriteriaBuilderFactory;
import com.blazebit.persistence.spi.CriteriaBuilderConfiguration;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Bean setup buat Blaze-Persistence, satu-satunya query tool project ini (QueryDSL sudah
 * dicabut - lihat pom.xml). {@link CriteriaBuilderFactory} - query builder mentah di atas
 * JPA Criteria, dipakai langsung untuk query BERAT (multi-join, filter dinamis, DTO
 * projection lewat {@code selectNew(...)}, pagination), lihat
 * {@code LoanApplicationRepository#search}. Dibangun di atas {@link EntityManagerFactory}
 * yang SAMA dengan yang dipegang *Repository lain - jadi query lewat sini tetap ikut
 * transaksi Spring yang sama (@Transactional di Service, bukan di sini).
 * <p>
 * Bean {@code EntityViewManager}/{@code EntityViewConfiguration} (lapisan Blaze-Persistence
 * Entity View di atas CriteriaBuilderFactory, dipakai proyeksi {@code @EntityView}) sempat
 * ada di sini tapi sudah dicabut - satu-satunya pemakainya ({@code RefCodeView} +
 * {@code RefCodeRepositoryImpl}) sudah dihapus dari codebase.
 */
@Configuration
public class QueryConfig {

    @Bean
    public CriteriaBuilderFactory criteriaBuilderFactory(EntityManagerFactory entityManagerFactory) {
        CriteriaBuilderConfiguration config = Criteria.getDefault();
        return config.createCriteriaBuilderFactory(entityManagerFactory);
    }
}
