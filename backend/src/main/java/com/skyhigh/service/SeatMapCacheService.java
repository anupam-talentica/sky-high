package com.skyhigh.service;

import com.skyhigh.dto.SeatMapResponseDTO;

import java.util.Optional;

/**
 * Cache for per-flight seat map data. Used to serve seat map reads with P95 &lt; 1s
 * and support hundreds of concurrent users. When cache is disabled or unavailable,
 * callers fall back to database.
 */
public interface SeatMapCacheService {

    /**
     * Get cached seat map for a flight, if present.
     *
     * @param flightId flight identifier
     * @return cached DTO or empty on miss/failure
     */
    Optional<SeatMapResponseDTO> get(String flightId);

    /**
     * Store seat map for a flight with configured TTL.
     *
     * @param flightId flight identifier
     * @param dto      seat map to cache
     */
    void put(String flightId, SeatMapResponseDTO dto);

    /**
     * Invalidate cached seat map for a flight (e.g. after seat state change).
     *
     * @param flightId flight identifier
     */
    void invalidate(String flightId);

    /**
     * Invalidate cached seat maps for multiple flights.
     *
     * @param flightIds flight identifiers
     */
    void invalidate(Iterable<String> flightIds);

    /**
     * Whether this implementation is actually caching (e.g. Redis vs no-op).
     */
    boolean isCaching();
}
