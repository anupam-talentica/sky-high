package com.skyhigh.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skyhigh.dto.SeatMapResponseDTO;
import com.skyhigh.config.RedisSeatMapCacheConfig;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Redis-backed seat map cache. Key scheme: seatmap:flight:{flightId}. TTL from config (e.g. 30s).
 * On Redis failure, operations are no-ops / return empty so callers fall back to DB.
 * Instantiated by {@link com.skyhigh.config.RedisSeatMapCacheConfig} when Redis cache is enabled.
 */
public class RedisSeatMapCacheService implements SeatMapCacheService {

    private static final Logger logger = LoggerFactory.getLogger(RedisSeatMapCacheService.class);
    private static final TypeReference<SeatMapResponseDTO> DTO_TYPE = new TypeReference<>() {};

    private final RedisTemplate<String, String> redis;
    private final ObjectMapper objectMapper;
    private final long ttlSeconds;

    private final Counter cacheHitCounter;
    private final Counter cacheMissCounter;
    private final Counter cachePutCounter;
    private final Counter cacheInvalidateCounter;
    private final Counter cacheErrorCounter;
    private final Timer cacheGetTimer;

    public RedisSeatMapCacheService(
            RedisTemplate<String, String> redisSeatMapTemplate,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry,
            int ttlSeconds) {
        this.redis = redisSeatMapTemplate;
        this.objectMapper = objectMapper;
        this.ttlSeconds = ttlSeconds;

        this.cacheHitCounter = Counter.builder("seatmap.cache.hits")
                .description("Seat map cache hits")
                .register(meterRegistry);
        this.cacheMissCounter = Counter.builder("seatmap.cache.misses")
                .description("Seat map cache misses")
                .register(meterRegistry);
        this.cachePutCounter = Counter.builder("seatmap.cache.puts")
                .description("Seat map cache puts")
                .register(meterRegistry);
        this.cacheInvalidateCounter = Counter.builder("seatmap.cache.invalidations")
                .description("Seat map cache invalidations")
                .register(meterRegistry);
        this.cacheErrorCounter = Counter.builder("seatmap.cache.errors")
                .description("Seat map cache errors (fallback to DB)")
                .register(meterRegistry);
        this.cacheGetTimer = Timer.builder("seatmap.cache.get.duration")
                .description("Seat map cache get latency")
                .register(meterRegistry);
    }

    @Override
    public Optional<SeatMapResponseDTO> get(String flightId) {
        String key = RedisSeatMapCacheConfig.CACHE_KEY_PREFIX + flightId;
        return cacheGetTimer.record(() -> {
            try {
                String json = redis.opsForValue().get(key);
                if (json == null || json.isEmpty()) {
                    cacheMissCounter.increment();
                    return Optional.empty();
                }
                SeatMapResponseDTO dto = objectMapper.readValue(json, DTO_TYPE);
                cacheHitCounter.increment();
                return Optional.of(dto);
            } catch (JsonProcessingException e) {
                logger.warn("Seat map cache deserialization failed for flight {}: {}", flightId, e.getMessage());
                cacheErrorCounter.increment();
                cacheMissCounter.increment();
                return Optional.empty();
            } catch (Exception e) {
                logger.warn("Seat map cache get failed for flight {}: {}", flightId, e.getMessage());
                cacheErrorCounter.increment();
                cacheMissCounter.increment();
                return Optional.empty();
            }
        });
    }

    @Override
    public void put(String flightId, SeatMapResponseDTO dto) {
        String key = RedisSeatMapCacheConfig.CACHE_KEY_PREFIX + flightId;
        try {
            String json = objectMapper.writeValueAsString(dto);
            redis.opsForValue().set(key, json, Duration.ofSeconds(ttlSeconds));
            cachePutCounter.increment();
        } catch (JsonProcessingException e) {
            logger.warn("Seat map cache serialization failed for flight {}: {}", flightId, e.getMessage());
            cacheErrorCounter.increment();
        } catch (Exception e) {
            logger.warn("Seat map cache put failed for flight {}: {}", flightId, e.getMessage());
            cacheErrorCounter.increment();
        }
    }

    @Override
    public void invalidate(String flightId) {
        String key = RedisSeatMapCacheConfig.CACHE_KEY_PREFIX + flightId;
        try {
            Boolean removed = redis.delete(key);
            if (Boolean.TRUE.equals(removed)) {
                cacheInvalidateCounter.increment();
            }
        } catch (Exception e) {
            logger.warn("Seat map cache invalidate failed for flight {}: {}", flightId, e.getMessage());
            cacheErrorCounter.increment();
        }
    }

    @Override
    public void invalidate(Iterable<String> flightIds) {
        List<String> keys = new ArrayList<>();
        for (String flightId : flightIds) {
            keys.add(RedisSeatMapCacheConfig.CACHE_KEY_PREFIX + flightId);
        }
        if (keys.isEmpty()) {
            return;
        }
        try {
            Long removed = redis.delete(keys);
            if (removed != null && removed > 0) {
                cacheInvalidateCounter.increment(removed);
            }
        } catch (Exception e) {
            logger.warn("Seat map cache batch invalidate failed: {}", e.getMessage());
            cacheErrorCounter.increment();
        }
    }

    @Override
    public boolean isCaching() {
        return true;
    }
}
