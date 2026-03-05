package com.skyhigh.config;

import com.skyhigh.service.RedisSeatMapAbuseDetectionService;
import com.skyhigh.service.SeatMapAbuseDetectionService;
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
 * Redis-backed abuse / rate limiting for seat map access.
 * <p>
 * Active only when app.rate-limit.seat-map.enabled=true.
 * Reuses the main Redis connection via Spring Boot auto-configuration.
 */
@Configuration
@ConditionalOnProperty(name = "app.rate-limit.seat-map.enabled", havingValue = "true")
@Import(RedisAutoConfiguration.class)
public class RedisSeatMapAbuseDetectionConfig {

    @Bean
    public SeatMapAbuseDetectionService redisSeatMapAbuseDetectionService(
            RedisTemplate<String, String> redisSeatMapAbuseTemplate,
            MeterRegistry meterRegistry,
            @Value("${app.rate-limit.seat-map.window-seconds:2}") int windowSeconds,
            @Value("${app.rate-limit.seat-map.threshold:50}") long threshold,
            @Value("${app.rate-limit.seat-map.block-seconds:120}") int blockSeconds
    ) {
        return new RedisSeatMapAbuseDetectionService(
                redisSeatMapAbuseTemplate,
                meterRegistry,
                windowSeconds,
                threshold,
                blockSeconds
        );
    }

    @Bean
    public RedisTemplate<String, String> redisSeatMapAbuseTemplate(
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

