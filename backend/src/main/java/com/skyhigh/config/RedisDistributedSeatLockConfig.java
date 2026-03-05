package com.skyhigh.config;

import com.skyhigh.service.DistributedSeatLockService;
import com.skyhigh.service.RedisDistributedSeatLockService;
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
 * Redis-backed distributed seat lock. Active only when app.redis.distributed-seat-lock-enabled=true.
 * When enabled, imports Redis auto-configuration so Redis connection is available.
 * Enable when running multiple backend instances to avoid duplicate seat assignments under load.
 */
@Configuration
@ConditionalOnProperty(name = "app.redis.distributed-seat-lock-enabled", havingValue = "true")
@Import(RedisAutoConfiguration.class)
public class RedisDistributedSeatLockConfig {

    @Bean
    public DistributedSeatLockService redisDistributedSeatLockService(
            RedisTemplate<String, String> redisSeatLockTemplate,
            @Value("${app.redis.seat-lock-ttl-seconds:10}") int seatLockTtlSeconds) {
        return new RedisDistributedSeatLockService(redisSeatLockTemplate, seatLockTtlSeconds);
    }

    @Bean
    public RedisTemplate<String, String> redisSeatLockTemplate(
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
