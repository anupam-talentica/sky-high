package com.skyhigh.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {
    
    @Bean
    public CacheManager cacheManager() {
        // Seat maps are served by Redis when enabled; Caffeine is used for
        // lightweight in-memory caches like flights, flightStatus, and AviationStack metadata.
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(
                "flights",
                "flightStatus",
                "aviationAirline",
                "aviationAirport"
        );
        cacheManager.setCaffeine(caffeineCacheBuilder());
        return cacheManager;
    }
    
    private Caffeine<Object, Object> caffeineCacheBuilder() {
        return Caffeine.newBuilder()
                .maximumSize(500)
                .expireAfterWrite(60, TimeUnit.SECONDS)
                .recordStats();
    }
}
