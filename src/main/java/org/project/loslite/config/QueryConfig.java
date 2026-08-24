package org.project.loslite.config;

import com.blazebit.persistence.Criteria;
import com.blazebit.persistence.CriteriaBuilderFactory;
import com.blazebit.persistence.spi.CriteriaBuilderConfiguration;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Bean setup buat Blaze-Persistence (CriteriaBuilderFactory) - satu-satunya query tool
 * yang dipakai project ini (QueryDSL murni/JPAQueryFactory sengaja tidak dipakai lagi,
 * lihat ApplicantRepository buat pola query-nya). EntityManagerFactory yang dipakai di
 * sini SAMA dengan yang dipegang JpaRepository biasa - jadi query lewat Blaze dan lewat
 * *Repository yang sudah ada tetap ikut transaksi Spring yang sama (@Transactional di
 * Service, bukan di sini).
 */
@Configuration
public class QueryConfig {

    @Bean
    public CriteriaBuilderFactory criteriaBuilderFactory(EntityManagerFactory entityManagerFactory) {
        CriteriaBuilderConfiguration config = Criteria.getDefault();
        return config.createCriteriaBuilderFactory(entityManagerFactory);
    }
}
