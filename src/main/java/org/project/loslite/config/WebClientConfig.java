package org.project.loslite.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    /**
     * service-token BELUM final (lihat komentar di application.yml) - untuk sekarang
     * dikirim mentah sebagai Bearer header ke SEMUA request lewat client ini. Kalau
     * kosong (default lokal), header Authorization sengaja tidak di-set sama sekali -
     * WORKFLOW-APP akan menolak dengan 401, itu diharapkan sampai token asli dipasang.
     */
    @Bean
    public WebClient workflowAppWebClient(
            @Value("${workflow-app.base-url}") String baseUrl,
            @Value("${workflow-app.service-token}") String serviceToken) {

        WebClient.Builder builder = WebClient.builder().baseUrl(baseUrl);

        if (serviceToken != null && !serviceToken.isBlank()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + serviceToken);
        }

        return builder.build();
    }
}
