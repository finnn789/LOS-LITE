package org.project.loslite.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    /**
     * WORKFLOW (org.lite.project.workflow) mewajibkan HTTP Basic Auth di semua endpoint
     * {@code /api/**} (lihat dokumentasi API WORKFLOW) - beda dari WORKFLOW-APP dulu yang
     * butuh Bearer service-token. Kredensial dipasang sebagai default header di sini
     * ({@code workflow-app.username}/{@code workflow-app.password}, lihat
     * application.yml) supaya {@link org.project.loslite.component.WorkflowAppClient}
     * tidak perlu tahu detail auth-nya sama sekali - tiap request lewat WebClient ini
     * otomatis kebawa header {@code Authorization: Basic ...}.
     */
    @Bean
    public WebClient workflowAppWebClient(
            @Value("${workflow-app.base-url}") String baseUrl,
            @Value("${workflow-app.username}") String username,
            @Value("${workflow-app.password}") String password) {

        return WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeaders(headers -> headers.setBasicAuth(username, password))
                .build();
    }
}
