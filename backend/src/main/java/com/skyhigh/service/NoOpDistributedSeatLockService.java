package com.skyhigh.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * No-op implementation when distributed seat lock is disabled. All lock attempts succeed with a dummy token;
 * unlock is a no-op. Reservation relies only on DB optimistic locking.
 */
@Service
@ConditionalOnProperty(name = "app.redis.distributed-seat-lock-enabled", havingValue = "false", matchIfMissing = true)
public class NoOpDistributedSeatLockService implements DistributedSeatLockService {

    private static final String NOOP_TOKEN = "noop";

    @Override
    public Optional<String> tryLock(String flightId, String seatNumber) {
        return Optional.of(NOOP_TOKEN);
    }

    @Override
    public void unlock(String flightId, String seatNumber, String token) {
        // no-op
    }

    @Override
    public boolean isLocking() {
        return false;
    }
}
