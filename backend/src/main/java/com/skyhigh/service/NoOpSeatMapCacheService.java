package com.skyhigh.service;

import com.skyhigh.dto.SeatMapResponseDTO;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * No-op cache when Redis seat map cache is disabled. All reads miss, put/invalidate are no-ops.
 */
@Service
@ConditionalOnProperty(name = "app.redis.seat-map-cache-enabled", havingValue = "false")
public class NoOpSeatMapCacheService implements SeatMapCacheService {

    @Override
    public Optional<SeatMapResponseDTO> get(String flightId) {
        return Optional.empty();
    }

    @Override
    public void put(String flightId, SeatMapResponseDTO dto) {
        // no-op
    }

    @Override
    public void invalidate(String flightId) {
        // no-op
    }

    @Override
    public void invalidate(Iterable<String> flightIds) {
        // no-op
    }

    @Override
    public boolean isCaching() {
        return false;
    }
}
