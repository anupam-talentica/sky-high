# Task: Distributed Seat Lock (Redis) — Optional

**Source:** [performance-optimization.md](../../performance-optimization.md) — Must-have 3 (optional Redis)

## Objective

When running multiple backend instances, use Redis to provide a distributed lock around the critical section of seat reservation so that only one node can assign a given seat at a time, reinforcing conflict-free behavior under high request volume.

## Problem Statement (exact)

- "Seat assignment must remain correct regardless of request volume"
- "No race condition should result in duplicate seat assignments"
- "The system must remain consistent under concurrent usage"

## Scope

- Implement Redis-based lock per seat (or per flight) for the reserve/assign path.
- Lock must have short TTL to avoid deadlocks; reserve logic must still use DB optimistic locking (or atomic update) as the source of truth.
- Make this optional (e.g. feature flag or only when Redis is enabled and multi-node is desired).

## Key Deliverables

### 1. Lock Design

- [x] Choose lock key scheme (e.g. `lock:seat:{seatId}` or `lock:flight:{flightId}`). **Implemented:** `lock:seat:{flightId}:{seatNumber}`.
- [x] Use a proven pattern: Redisson `RLock`, or SET key NX PX (or SET with NX + EX) with unique token and Lua unlock script. **Implemented:** SET NX PX with UUID token + Lua script for unlock.
- [x] Set lock TTL (e.g. 5–15 seconds) so that crashes do not leave seats permanently locked. **Implemented:** `app.redis.seat-lock-ttl-seconds` (default 10).

### 2. Integration with Reserve Flow

- [x] Before updating seat state to HELD in DB: acquire Redis lock for that seat (or flight).
- [x] If lock cannot be acquired, return conflict/retry response (e.g. 409 or 503 with Retry-After). **Implemented:** 503 + `SeatLockConflictException` with `Retry-After` header.
- [x] After DB update (success or failure), release lock using same token; do not release if lock was not held by this request.
- [x] Keep DB optimistic locking (e.g. `@Version`) as primary consistency mechanism; Redis lock reduces contention and retries across nodes.

### 3. Safety and Robustness

- [x] Use a unique lock value (UUID) per request so only the holder can unlock.
- [x] Unlock only when holding the lock (check value in Lua or GET+DEL with check).
- [x] If Redis is unavailable, fall back to DB-only path (no distributed lock) and document behavior.

### 4. Configuration

- [x] Make distributed lock feature-flagged or conditional on Redis + multi-node. **Implemented:** `app.redis.distributed-seat-lock-enabled` (default `false`).
- [x] Document when to enable (e.g. more than one backend instance and high DB contention). See `application.yml` and `backend/DISTRIBUTED_SEAT_LOCK.md`.

## Acceptance Criteria

- With Redis and distributed lock enabled, concurrent reserve requests for the same seat from different nodes do not both succeed; one gets the lock and proceeds, others get conflict/retry.
- Lock TTL ensures that a crashed node does not hold the lock indefinitely.
- With Redis disabled or lock disabled, reservation still works using only DB optimistic locking.

## References

- [performance-optimization.md](../../performance-optimization.md) — Section 3 (Must-have 3), Section 5 (Redis vs Elasticsearch).
