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

@Configuration
@ConfigurationProperties(prefix = "external.weatherapi")
@Data
@Slf4j
public class WeatherApiConfig {

    /**
     * API key for WeatherAPI. Should be configured via environment variable outside of local/dev.
     */
    private String apiKey;

    /**
     * Base URL for WeatherAPI.
     */
    private String baseUrl = "https://api.weatherapi.com/v1";

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
            log.warn("WeatherAPI integration is enabled but API key is not configured. " +
                    "External weather lookups will fail until WEATHERAPI_API_KEY is set.");
        }
    }

    @Bean
    @ConditionalOnProperty(name = "external.weatherapi.enabled", havingValue = "true", matchIfMissing = true)
    public RestTemplate weatherApiRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);

        return new RestTemplate(factory);
    }
}

