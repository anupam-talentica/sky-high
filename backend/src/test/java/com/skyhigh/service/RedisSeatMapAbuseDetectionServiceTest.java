package com.skyhigh.service;

import com.skyhigh.exception.RateLimitExceededException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class RedisSeatMapAbuseDetectionServiceTest {

    private RedisTemplate<String, String> redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private RedisSeatMapAbuseDetectionService service;

    @BeforeEach
    void setUp() {
        //noinspection unchecked
        redisTemplate = (RedisTemplate<String, String>) Mockito.mock(RedisTemplate.class);
        //noinspection unchecked
        valueOperations = (ValueOperations<String, String>) Mockito.mock(ValueOperations.class);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        service = new RedisSeatMapAbuseDetectionService(
                redisTemplate,
                new SimpleMeterRegistry(),
                2,
                3,   // low threshold for test
                60
        );
    }

    @Test
    void checkSeatMapAccessAllowed_whenUnderThreshold_shouldNotThrow() {
        when(redisTemplate.getExpire(any(String.class))).thenReturn(-1L);
        when(valueOperations.increment(any(String.class))).thenReturn(1L, 2L, 3L);

        assertDoesNotThrow(() -> {
            service.checkSeatMapAccessAllowed("source-1");
            service.checkSeatMapAccessAllowed("source-1");
            service.checkSeatMapAccessAllowed("source-1");
        });
    }

    @Test
    void checkSeatMapAccessAllowed_whenOverThreshold_shouldThrowRateLimitExceededException() {
        when(redisTemplate.getExpire(any(String.class))).thenReturn(-1L);
        when(valueOperations.increment(any(String.class))).thenReturn(1L, 2L, 4L);
        when(redisTemplate.getExpire(eq("abuse:seatmap:block:source-2"))).thenReturn(60L);

        assertThrows(RateLimitExceededException.class, () -> {
            service.checkSeatMapAccessAllowed("source-2");
            service.checkSeatMapAccessAllowed("source-2");
            service.checkSeatMapAccessAllowed("source-2");
        });
    }

    @Test
    void checkSeatMapAccessAllowed_whenAlreadyBlocked_shouldThrowWithRetryAfter() {
        when(redisTemplate.getExpire(eq("abuse:seatmap:block:source-3"))).thenReturn(10L);

        RateLimitExceededException ex = assertThrows(RateLimitExceededException.class, () ->
                service.checkSeatMapAccessAllowed("source-3")
        );

        assertEquals(10, ex.getRetryAfterSeconds());
        verify(valueOperations, never()).increment(any(String.class));
    }

    @Test
    void checkSeatMapAccessAllowed_whenSourceBlank_shouldUseUnknownAndExpireWindow() {
        when(redisTemplate.getExpire(any(String.class))).thenReturn(-1L);
        when(valueOperations.increment(any(String.class))).thenReturn(1L);

        assertDoesNotThrow(() -> service.checkSeatMapAccessAllowed("   "));

        verify(redisTemplate).getExpire(eq("abuse:seatmap:block:unknown"));
        verify(valueOperations).increment(eq("abuse:seatmap:count:unknown"));
        verify(redisTemplate).expire(eq("abuse:seatmap:count:unknown"), eq(Duration.ofSeconds(2)));
    }

    @Test
    void checkSeatMapAccessAllowed_whenThresholdExceededWithMissingBlockTtl_shouldUseBlockSeconds() {
        when(redisTemplate.getExpire(any(String.class))).thenReturn(-1L);
        when(valueOperations.increment(any(String.class))).thenReturn(4L);
        when(redisTemplate.getExpire(eq("abuse:seatmap:block:source-4"))).thenReturn(null);

        RateLimitExceededException ex = assertThrows(RateLimitExceededException.class, () ->
                service.checkSeatMapAccessAllowed("source-4")
        );

        assertEquals(60, ex.getRetryAfterSeconds());
        verify(valueOperations).set(eq("abuse:seatmap:block:source-4"), eq("1"), eq(Duration.ofSeconds(60)));
    }

    @Test
    void checkSeatMapAccessAllowed_whenRedisError_shouldFailOpen() {
        when(redisTemplate.getExpire(any(String.class))).thenThrow(new RuntimeException("redis down"));

        assertDoesNotThrow(() -> service.checkSeatMapAccessAllowed("source-5"));
    }

    @Test
    void checkSeatMapAccessAllowed_whenCountIsNull_shouldNotThrow() {
        when(redisTemplate.getExpire(any(String.class))).thenReturn(-1L);
        when(valueOperations.increment(any(String.class))).thenReturn(null);

        assertDoesNotThrow(() -> service.checkSeatMapAccessAllowed("source-6"));
        verify(redisTemplate, never()).expire(any(String.class), any(Duration.class));
    }
}
