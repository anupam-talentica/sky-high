package com.skyhigh.service;

/**
 * Detects abusive seat map access patterns for a given source (e.g. client IP).
 * Backed by Redis when enabled; implementations should be safe to fail open
 * (i.e. do not break seat map flows if Redis is unavailable).
 */
public interface SeatMapAbuseDetectionService {

    /**
     * Check whether the given source is allowed to access seat map endpoints.
     * <p>
     * Implementations should:
     * - detect if the source is currently blocked and, if so, throw a rate-limit exception
     * - increment the access counter inside a short time window (e.g. 2 seconds)
     * - when the counter exceeds the configured threshold, set a temporary block
     *
     * @param sourceId identifier of the source (e.g. IP address)
     */
    void checkSeatMapAccessAllowed(String sourceId);
}

