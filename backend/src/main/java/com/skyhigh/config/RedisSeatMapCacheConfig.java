package com.skyhigh.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skyhigh.service.RedisSeatMapCacheService;
import com.skyhigh.service.SeatMapCacheService;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis-backed seat map cache. Active only when app.redis.seat-map-cache-enabled=true.
 * When enabled, imports Redis auto-configuration so Redis connection is available.
 */
@Configuration
@ConditionalOnProperty(name = "app.redis.seat-map-cache-enabled", havingValue = "true")
@Import(RedisAutoConfiguration.class)
public class RedisSeatMapCacheConfig {

    /** Cache key prefix: seatmap:flight:{flightId} */
    public static final String CACHE_KEY_PREFIX = "seatmap:flight:";

    @Bean
    public SeatMapCacheService redisSeatMapCacheService(
            RedisTemplate<String, String> redisSeatMapTemplate,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry,
            @Value("${app.redis.seat-map-cache-ttl-seconds:30}") int seatMapCacheTtlSeconds) {
        return new RedisSeatMapCacheService(redisSeatMapTemplate, objectMapper, meterRegistry, seatMapCacheTtlSeconds);
    }

    @Bean
    public RedisTemplate<String, String> redisSeatMapTemplate(
            org.springframework.data.redis.connection.RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(RedisSerializer.string());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(RedisSerializer.string());
        template.afterPropertiesSet();
        return template;
    }
}
