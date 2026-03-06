package com.skyhigh.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis-backed notification queue publisher.
 * <p>
 * Active when notifications.enabled=true (default).
 * Reuses the main Redis connection via Spring Boot auto-configuration.
 */
@Configuration
@ConditionalOnProperty(name = "notifications.enabled", havingValue = "true", matchIfMissing = true)
@Import(RedisAutoConfiguration.class)
public class RedisNotificationConfig {

    @Bean
    public RedisTemplate<String, String> redisNotificationTemplate(
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

