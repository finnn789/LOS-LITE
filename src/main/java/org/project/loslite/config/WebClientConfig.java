package org.project.loslite.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    /**
     * WORKFLOW (org.lite.project.workflow) belum punya auth sama sekali di endpoint
     * REST-nya - beda dari WORKFLOW-APP dulu yang butuh Bearer service-token. Kalau
     * WORKFLOW nanti ditambah auth, header default-nya dipasang lagi di sini (lihat
     * histori git untuk pola Bearer header yang sebelumnya ada).
     */
    @Bean
    public WebClient workflowAppWebClient(@Value("${workflow-app.base-url}") String baseUrl) {

        return WebClient.builder().baseUrl(baseUrl).build();
    }
}
