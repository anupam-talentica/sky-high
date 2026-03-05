package com.skyhigh.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

class RedisDistributedSeatLockServiceTest {

    private RedisTemplate<String, String> redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private RedisDistributedSeatLockService service;

    @BeforeEach
    void setUp() {
        redisTemplate = mockRedisTemplate();
        valueOperations = mockValueOperations();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        service = new RedisDistributedSeatLockService(redisTemplate, 5); // 5 seconds TTL
    }

    @Test
    void tryLock_whenAcquired_shouldReturnTokenAndUseCorrectKeyAndTtl() {
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(Boolean.TRUE);

        Optional<String> result = service.tryLock("F1", "1A");

        assertTrue(result.isPresent());

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Duration> durationCaptor = ArgumentCaptor.forClass(Duration.class);

        verify(valueOperations).setIfAbsent(keyCaptor.capture(), tokenCaptor.capture(), durationCaptor.capture());

        assertEquals("lock:seat:F1:1A", keyCaptor.getValue());
        assertEquals(result.get(), tokenCaptor.getValue());
        assertEquals(Duration.ofSeconds(5), durationCaptor.getValue());
    }

    @Test
    void tryLock_whenAlreadyLocked_shouldReturnEmpty() {
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(Boolean.FALSE);

        Optional<String> result = service.tryLock("F2", "2B");

        assertFalse(result.isPresent());
    }

    @Test
    void tryLock_whenRedisError_shouldReturnFallbackToken() {
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenThrow(new RuntimeException("redis down"));

        Optional<String> result = service.tryLock("F3", "3C");

        assertTrue(result.isPresent());
        assertEquals("fallback", result.get());
    }

    @Test
    void unlock_whenFallbackToken_shouldDoNothing() {
        service.unlock("F4", "4D", "fallback");

        verify(redisTemplate, never()).execute(any(), any(List.class), any());
    }

    @Test
    void unlock_whenTokenMatches_shouldExecuteUnlockScriptWithCorrectKey() {
        when(redisTemplate.execute(any(), any(List.class), any()))
                .thenReturn(1L);

        service.unlock("F5", "5E", "token-123");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> keysCaptor =
                ArgumentCaptor.forClass((Class<List<String>>) (Class<?>) List.class);
        verify(redisTemplate).execute(any(), keysCaptor.capture(), any());

        assertEquals(Collections.singletonList("lock:seat:F5:5E"), keysCaptor.getValue());
    }

    @Test
    void unlock_whenRedisError_shouldSwallowException() {
        when(redisTemplate.execute(any(), any(List.class), any()))
                .thenThrow(new RuntimeException("redis down"));

        service.unlock("F6", "6F", "token-456");

        // no exception should be thrown
    }

    @Test
    void isLocking_shouldReturnTrue() {
        assertTrue(service.isLocking());
    }

    @SuppressWarnings("unchecked")
    private RedisTemplate<String, String> mockRedisTemplate() {
        return (RedisTemplate<String, String>) mock(RedisTemplate.class);
    }

    @SuppressWarnings("unchecked")
    private ValueOperations<String, String> mockValueOperations() {
        return (ValueOperations<String, String>) mock(ValueOperations.class);
    }
}

