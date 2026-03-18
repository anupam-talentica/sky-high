package com.skyhigh.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@ConfigurationProperties(prefix = "external.aviationstack")
@Data
@Slf4j
public class AviationStackConfig {

    /**
     * API key for AviationStack. Must be configured via environment variable in non-local environments.
     */
    private String apiKey;

    /**
     * Base URL for AviationStack API.
     */
    private String baseUrl = "http://api.aviationstack.com/v1";

    /**
     * HTTP timeout in milliseconds.
     */
    private Integer timeout = 5000;

    /**
     * Feature flag to enable / disable integration.
     */
    private Boolean enabled = Boolean.TRUE;

    @PostConstruct
    public void validateConfiguration() {
        if (Boolean.TRUE.equals(enabled) && (apiKey == null || apiKey.isBlank())) {
            log.warn("AviationStack integration is enabled but API key is not configured. " +
                    "External flight status lookups will fail until AVIATIONSTACK_API_KEY is set.");
        }
    }

    @Bean
    @ConditionalOnProperty(name = "external.aviationstack.enabled", havingValue = "true", matchIfMissing = true)
    public RestTemplate aviationStackRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);

        RestTemplate restTemplate = new RestTemplate(factory);
        return restTemplate;
    }

    @Bean
    @ConditionalOnProperty(name = "external.aviationstack.enabled", havingValue = "true", matchIfMissing = true)
    public WebClient aviationStackWebClient() {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .build();
    }
}

