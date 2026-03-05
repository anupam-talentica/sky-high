package com.skyhigh.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skyhigh.config.RedisSeatMapCacheConfig;
import com.skyhigh.dto.SeatMapResponseDTO;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisSeatMapCacheServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private ObjectMapper objectMapper;

    private SimpleMeterRegistry meterRegistry;

    private RedisSeatMapCacheService cacheService;

    private static final int TTL_SECONDS = 30;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        cacheService = new RedisSeatMapCacheService(redisTemplate, objectMapper, meterRegistry, TTL_SECONDS);
    }

    private double counterValue(String name) {
        Counter counter = meterRegistry.find(name).counter();
        assertNotNull(counter, "Counter " + name + " should be registered");
        return counter.count();
    }

    @Test
    void get_WhenCacheHit_ShouldReturnDtoAndIncrementHitCounter() throws Exception {
        String flightId = "SK1234";
        String key = RedisSeatMapCacheConfig.CACHE_KEY_PREFIX + flightId;
        SeatMapResponseDTO dto = SeatMapResponseDTO.builder()
                .flightId(flightId)
                .totalSeats(10)
                .availableSeats(5)
                .heldSeats(3)
                .confirmedSeats(2)
                .seats(List.of())
                .build();

        when(valueOperations.get(key)).thenReturn("json");
        when(objectMapper.readValue(eq("json"), any(TypeReference.class))).thenReturn(dto);

        Optional<SeatMapResponseDTO> result = cacheService.get(flightId);

        assertTrue(result.isPresent());
        assertSame(dto, result.get());
        assertEquals(1.0, counterValue("seatmap.cache.hits"));
        assertEquals(0.0, counterValue("seatmap.cache.misses"));
        assertEquals(0.0, counterValue("seatmap.cache.errors"));
    }

    @Test
    void get_WhenCacheMiss_ShouldReturnEmptyAndIncrementMissCounter() {
        String flightId = "SK9999";
        String key = RedisSeatMapCacheConfig.CACHE_KEY_PREFIX + flightId;

        when(valueOperations.get(key)).thenReturn(null);

        Optional<SeatMapResponseDTO> result = cacheService.get(flightId);

        assertTrue(result.isEmpty());
        assertEquals(0.0, counterValue("seatmap.cache.hits"));
        assertEquals(1.0, counterValue("seatmap.cache.misses"));
        assertEquals(0.0, counterValue("seatmap.cache.errors"));
    }

    @Test
    void get_WhenJsonProcessingException_ShouldReturnEmptyAndIncrementErrorAndMissCounters() throws Exception {
        String flightId = "SK1234";
        String key = RedisSeatMapCacheConfig.CACHE_KEY_PREFIX + flightId;

        when(valueOperations.get(key)).thenReturn("bad-json");
        when(objectMapper.readValue(eq("bad-json"), any(TypeReference.class)))
                .thenThrow(new JsonProcessingException("boom") {});

        Optional<SeatMapResponseDTO> result = cacheService.get(flightId);

        assertTrue(result.isEmpty());
        assertEquals(0.0, counterValue("seatmap.cache.hits"));
        assertEquals(1.0, counterValue("seatmap.cache.misses"));
        assertEquals(1.0, counterValue("seatmap.cache.errors"));
    }

    @Test
    void get_WhenRedisThrowsException_ShouldReturnEmptyAndIncrementErrorAndMissCounters() {
        String flightId = "SK1234";
        String key = RedisSeatMapCacheConfig.CACHE_KEY_PREFIX + flightId;

        when(valueOperations.get(key)).thenThrow(new RuntimeException("Redis down"));

        Optional<SeatMapResponseDTO> result = cacheService.get(flightId);

        assertTrue(result.isEmpty());
        assertEquals(0.0, counterValue("seatmap.cache.hits"));
        assertEquals(1.0, counterValue("seatmap.cache.misses"));
        assertEquals(1.0, counterValue("seatmap.cache.errors"));
    }

    @Test
    void put_WhenSuccessful_ShouldWriteToRedisAndIncrementPutCounter() throws Exception {
        String flightId = "SK1234";
        String key = RedisSeatMapCacheConfig.CACHE_KEY_PREFIX + flightId;
        SeatMapResponseDTO dto = SeatMapResponseDTO.builder()
                .flightId(flightId)
                .build();

        when(objectMapper.writeValueAsString(dto)).thenReturn("json");

        cacheService.put(flightId, dto);

        verify(valueOperations).set(eq(key), eq("json"), eq(Duration.ofSeconds(TTL_SECONDS)));
        assertEquals(1.0, counterValue("seatmap.cache.puts"));
        assertEquals(0.0, counterValue("seatmap.cache.errors"));
    }

    @Test
    void put_WhenJsonProcessingException_ShouldNotWriteAndShouldIncrementErrorCounter() throws Exception {
        String flightId = "SK1234";
        SeatMapResponseDTO dto = SeatMapResponseDTO.builder()
                .flightId(flightId)
                .build();

        when(objectMapper.writeValueAsString(dto)).thenThrow(new JsonProcessingException("boom") {});

        cacheService.put(flightId, dto);

        verify(valueOperations, never()).set(anyString(), anyString(), any());
        assertEquals(0.0, counterValue("seatmap.cache.puts"));
        assertEquals(1.0, counterValue("seatmap.cache.errors"));
    }

    @Test
    void put_WhenRedisThrowsException_ShouldIncrementErrorCounter() throws Exception {
        String flightId = "SK1234";
        String key = RedisSeatMapCacheConfig.CACHE_KEY_PREFIX + flightId;
        SeatMapResponseDTO dto = SeatMapResponseDTO.builder()
                .flightId(flightId)
                .build();

        when(objectMapper.writeValueAsString(dto)).thenReturn("json");
        doThrow(new RuntimeException("Redis down"))
                .when(valueOperations).set(eq(key), eq("json"), eq(Duration.ofSeconds(TTL_SECONDS)));

        cacheService.put(flightId, dto);

        assertEquals(0.0, counterValue("seatmap.cache.puts"));
        assertEquals(1.0, counterValue("seatmap.cache.errors"));
    }

    @Test
    void invalidateSingle_WhenDeleteReturnsTrue_ShouldIncrementInvalidateCounter() {
        String flightId = "SK1234";
        String key = RedisSeatMapCacheConfig.CACHE_KEY_PREFIX + flightId;

        when(redisTemplate.delete(key)).thenReturn(true);

        cacheService.invalidate(flightId);

        assertEquals(1.0, counterValue("seatmap.cache.invalidations"));
        assertEquals(0.0, counterValue("seatmap.cache.errors"));
    }

    @Test
    void invalidateSingle_WhenDeleteReturnsFalse_ShouldNotIncrementInvalidateCounter() {
        String flightId = "SK1234";
        String key = RedisSeatMapCacheConfig.CACHE_KEY_PREFIX + flightId;

        when(redisTemplate.delete(key)).thenReturn(false);

        cacheService.invalidate(flightId);

        assertEquals(0.0, counterValue("seatmap.cache.invalidations"));
        assertEquals(0.0, counterValue("seatmap.cache.errors"));
    }

    @Test
    void invalidateSingle_WhenRedisThrowsException_ShouldIncrementErrorCounter() {
        String flightId = "SK1234";
        String key = RedisSeatMapCacheConfig.CACHE_KEY_PREFIX + flightId;

        when(redisTemplate.delete(key)).thenThrow(new RuntimeException("Redis down"));

        cacheService.invalidate(flightId);

        assertEquals(0.0, counterValue("seatmap.cache.invalidations"));
        assertEquals(1.0, counterValue("seatmap.cache.errors"));
    }

    @Test
    void invalidateBatch_WhenIterableEmpty_ShouldDoNothing() {
        cacheService.invalidate(Collections.emptyList());

        verify(redisTemplate, never()).delete(anyList());
        assertEquals(0.0, counterValue("seatmap.cache.invalidations"));
        assertEquals(0.0, counterValue("seatmap.cache.errors"));
    }

    @Test
    void invalidateBatch_WhenKeysRemoved_ShouldIncrementInvalidateCounterByRemovedCount() {
        List<String> flightIds = List.of("SK1", "SK2", "SK3");

        when(redisTemplate.delete(anyList())).thenReturn(3L);

        cacheService.invalidate(flightIds);

        assertEquals(3.0, counterValue("seatmap.cache.invalidations"));
        assertEquals(0.0, counterValue("seatmap.cache.errors"));
    }

    @Test
    void invalidateBatch_WhenNoKeysRemoved_ShouldNotIncrementInvalidateCounter() {
        List<String> flightIds = List.of("SK1", "SK2");

        when(redisTemplate.delete(anyList())).thenReturn(0L);

        cacheService.invalidate(flightIds);

        assertEquals(0.0, counterValue("seatmap.cache.invalidations"));
        assertEquals(0.0, counterValue("seatmap.cache.errors"));
    }

    @Test
    void invalidateBatch_WhenRedisThrowsException_ShouldIncrementErrorCounter() {
        List<String> flightIds = List.of("SK1", "SK2");

        when(redisTemplate.delete(anyList())).thenThrow(new RuntimeException("Redis down"));

        cacheService.invalidate(flightIds);

        assertEquals(0.0, counterValue("seatmap.cache.invalidations"));
        assertEquals(1.0, counterValue("seatmap.cache.errors"));
    }

    @Test
    void isCaching_ShouldAlwaysReturnTrue() {
        assertTrue(cacheService.isCaching());
    }
}

