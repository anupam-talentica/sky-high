package com.skyhigh.service;

import com.skyhigh.exception.RateLimitExceededException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;

/**
 * Redis-backed implementation of {@link SeatMapAbuseDetectionService}.
 * <p>
 * Key scheme:
 * - Counter key: abuse:seatmap:count:{sourceId}
 * - Block key:   abuse:seatmap:block:{sourceId}
 * <p>
 * Algorithm:
 * - On each seat map access, first check for a block key; if present, throw {@link RateLimitExceededException}.
 * - Otherwise, increment the counter key with a short TTL window (e.g. 2 seconds).
 * - If the counter exceeds the configured threshold, set the block key with a block TTL and throw.
 * <p>
 * On any Redis error, this implementation fails open (logs and allows the request)
 * so that transient Redis issues do not break seat map flows.
 */
public class RedisSeatMapAbuseDetectionService implements SeatMapAbuseDetectionService {

    private static final Logger logger = LoggerFactory.getLogger(RedisSeatMapAbuseDetectionService.class);

    private static final String COUNTER_KEY_PREFIX = "abuse:seatmap:count:";
    private static final String BLOCK_KEY_PREFIX = "abuse:seatmap:block:";

    private final RedisTemplate<String, String> redisTemplate;
    private final int windowSeconds;
    private final long threshold;
    private final int blockSeconds;

    private final Counter blockedCounter;
    private final Counter errorCounter;

    public RedisSeatMapAbuseDetectionService(RedisTemplate<String, String> redisTemplate,
                                             MeterRegistry meterRegistry,
                                             int windowSeconds,
                                             long threshold,
                                             int blockSeconds) {
        this.redisTemplate = redisTemplate;
        this.windowSeconds = windowSeconds;
        this.threshold = threshold;
        this.blockSeconds = blockSeconds;

        this.blockedCounter = Counter.builder("seatmap.abuse.blocked")
                .description("Number of sources blocked by seat map abuse detection")
                .register(meterRegistry);

        this.errorCounter = Counter.builder("seatmap.abuse.errors")
                .description("Errors encountered while performing seat map abuse detection")
                .register(meterRegistry);
    }

    @Override
    public void checkSeatMapAccessAllowed(String sourceId) {
        if (sourceId == null || sourceId.isBlank()) {
            sourceId = "unknown";
        }

        String blockKey = BLOCK_KEY_PREFIX + sourceId;
        String counterKey = COUNTER_KEY_PREFIX + sourceId;

        try {
            // 1. Fast path: check if source is already blocked
            Long remainingBlockTtl = redisTemplate.getExpire(blockKey);
            if (remainingBlockTtl != null && remainingBlockTtl > 0) {
                blockedCounter.increment();
                logger.warn("Seat map access blocked for source {} (remaining block TTL {}s)", sourceId, remainingBlockTtl);
                throw new RateLimitExceededException(
                        "Too many seat map requests from this source. Please try again later.",
                        remainingBlockTtl.intValue()
                );
            }

            // 2. Increment counter within short time window
            Long count = redisTemplate.opsForValue().increment(counterKey);
            if (count != null && count == 1L) {
                // First hit in the window: set TTL
                redisTemplate.expire(counterKey, Duration.ofSeconds(windowSeconds));
            }

            if (count != null && count > threshold) {
                // 3. Threshold exceeded: set block key and throw
                redisTemplate.opsForValue().set(blockKey, "1", Duration.ofSeconds(blockSeconds));
                Long newTtl = redisTemplate.getExpire(blockKey);
                long retryAfterSeconds = (newTtl != null && newTtl > 0) ? newTtl : blockSeconds;

                blockedCounter.increment();
                logger.warn(
                        "Seat map abuse detected for source {}: count {} in {}s window (threshold {}), blocking for {}s",
                        sourceId, count, windowSeconds, threshold, retryAfterSeconds
                );

                throw new RateLimitExceededException(
                        "Too many seat map requests from this source. Please try again later.",
                        (int) retryAfterSeconds
                );
            }
        } catch (RateLimitExceededException ex) {
            // Propagate rate-limit signal as-is
            throw ex;
        } catch (Exception ex) {
            // Fail open: log and allow the request to proceed
            errorCounter.increment();
            logger.warn("Seat map abuse detection failed for source {}: {}", sourceId, ex.getMessage());
        }
    }
}

