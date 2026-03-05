package com.skyhigh.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

/**
 * Redis-based distributed lock for seat reservation. Uses SET key NX PX with a unique token;
 * unlock via Lua script so only the holder can release. When Redis is unavailable, falls back
 * to DB-only (no distributed lock) so reservation still works.
 */
public class RedisDistributedSeatLockService implements DistributedSeatLockService {

    private static final Logger logger = LoggerFactory.getLogger(RedisDistributedSeatLockService.class);

    /** Lock key: lock:seat:{flightId}:{seatNumber} */
    private static final String LOCK_KEY_PREFIX = "lock:seat:";

    /** Token used when Redis fails and we fall back to DB-only (no lock acquired in Redis). */
    private static final String FALLBACK_TOKEN = "fallback";

    /** Lua: delete key only if value matches (only holder can unlock). */
    private static final String UNLOCK_SCRIPT =
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";

    private final RedisTemplate<String, String> redis;
    private final long ttlMillis;
    private final DefaultRedisScript<Long> unlockScript;

    public RedisDistributedSeatLockService(RedisTemplate<String, String> redis, long ttlSeconds) {
        this.redis = redis;
        this.ttlMillis = ttlSeconds * 1000L;
        this.unlockScript = new DefaultRedisScript<>(UNLOCK_SCRIPT, Long.class);
    }

    @Override
    public Optional<String> tryLock(String flightId, String seatNumber) {
        String key = lockKey(flightId, seatNumber);
        String token = UUID.randomUUID().toString();
        try {
            Boolean acquired = redis.opsForValue().setIfAbsent(key, token, java.time.Duration.ofMillis(ttlMillis));
            if (Boolean.TRUE.equals(acquired)) {
                logger.debug("Acquired seat lock key={}", key);
                return Optional.of(token);
            }
            logger.debug("Could not acquire seat lock key={}", key);
            return Optional.empty();
        } catch (Exception e) {
            logger.warn("Redis unavailable during tryLock for seat {} on flight {}; falling back to DB-only: {}",
                    seatNumber, flightId, e.getMessage());
            return Optional.of(FALLBACK_TOKEN);
        }
    }

    @Override
    public void unlock(String flightId, String seatNumber, String token) {
        if (FALLBACK_TOKEN.equals(token)) {
            return;
        }
        String key = lockKey(flightId, seatNumber);
        try {
            Long deleted = redis.execute(unlockScript, Collections.singletonList(key), token);
            if (Long.valueOf(1).equals(deleted)) {
                logger.debug("Released seat lock key={}", key);
            }
        } catch (Exception e) {
            logger.warn("Redis error during unlock for seat {} on flight {}: {}", seatNumber, flightId, e.getMessage());
        }
    }

    @Override
    public boolean isLocking() {
        return true;
    }

    private static String lockKey(String flightId, String seatNumber) {
        return LOCK_KEY_PREFIX + flightId + ":" + seatNumber;
    }
}
