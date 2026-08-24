package org.project.loslite.config;

import org.project.loslite.service.DtiCalculator;
import org.project.loslite.service.LoanStatusTransitionValidator;
import org.project.loslite.service.RuleEngine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Domain service (DtiCalculator, RuleEngine, LoanStatusTransitionValidator) SENGAJA
 * tidak dikasih @Component/@Service - domain layer harus zero-Spring-import, murni
 * Java, supaya bisa di-unit-test tanpa Spring context sama sekali.
 * <p>
 * Konsekuensinya: Spring tidak tahu cara bikin instance-nya otomatis lewat
 * component scanning, jadi harus didaftarkan manual sebagai @Bean di sini
 * (infrastructure layer - satu-satunya tempat yang boleh "menikahkan" domain
 * class ke Spring container).
 */
@Configuration
public class DomainServiceConfig {

    @Bean
    public DtiCalculator dtiCalculator() {
        return new DtiCalculator();
    }

    @Bean
    public RuleEngine ruleEngine(DtiCalculator dtiCalculator) {
        return new RuleEngine(dtiCalculator);
    }

    @Bean
    public LoanStatusTransitionValidator loanStatusTransitionValidator() {
        return new LoanStatusTransitionValidator();
    }
}
