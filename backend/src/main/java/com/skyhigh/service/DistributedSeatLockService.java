package com.skyhigh.service;

import java.util.Optional;

/**
 * Distributed lock for seat reservation to prevent duplicate assignments across nodes.
 * When enabled (Redis), only one node can assign a given seat at a time.
 * When disabled (no-op), no distributed locking is performed; DB optimistic locking remains the source of truth.
 */
public interface DistributedSeatLockService {

    /**
     * Try to acquire a lock for the given seat. Lock key is derived from flight and seat number.
     *
     * @param flightId   flight identifier
     * @param seatNumber seat number
     * @return optional lock token if lock was acquired; empty if lock is held by another request
     */
    Optional<String> tryLock(String flightId, String seatNumber);

    /**
     * Release the lock using the token returned by tryLock. Only the holder of the lock (same token) can release it.
     *
     * @param flightId   flight identifier
     * @param seatNumber seat number
     * @param token      token returned by tryLock
     */
    void unlock(String flightId, String seatNumber, String token);

    /**
     * Whether this implementation actually performs distributed locking (e.g. Redis). When false, tryLock always
     * succeeds and unlock is a no-op.
     */
    boolean isLocking();
}
