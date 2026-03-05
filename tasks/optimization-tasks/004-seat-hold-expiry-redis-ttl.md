# Task: Seat Hold Expiry with Redis TTL (Optional)

**Source:** [performance-optimization.md](../../performance-optimization.md) — Must-have 4 (optional Redis)

## Objective

Optionally use Redis TTL (and optionally keyspace notifications) to trigger or coordinate release of expired seat holds for more granular expiry, while still persisting final state in the DB and invalidating the seat map cache.

## Problem Statement (exact)

- "This behavior must work reliably even during high traffic"
- "If the passenger does not complete check-in within the time window: The seat must automatically become AVAILABLE again"

## Scope

- When a seat is set to HELD, optionally create a Redis key with TTL = 120 seconds (or `held_until - now()`).
- On key expiry (or via keyspace notification), trigger release of that seat in the DB and invalidate seat map cache for the flight.
- Ensure release logic uses same concurrency controls as reservation (optimistic lock or atomic update).
- Keep existing scheduler-based release as primary or fallback so behavior is reliable when Redis is down.

## Key Deliverables

### 1. Redis Key for Hold Expiry

- [ ] Define key scheme (e.g. `hold:seat:{seatId}` or `hold:seat:{seatId}:{reservationId}`).
- [ ] When seat moves to HELD: set key in Redis with value that identifies the hold (e.g. seat ID or reservation ID) and TTL = remaining hold time (max 120 seconds).
- [ ] If Redis is unavailable, do not fail the hold; rely on scheduler to release.

### 2. Expiry Handling

- [ ] **Option A:** Use Redis keyspace notifications (configurable in Redis). Subscribe to `expired` events; when `hold:seat:*` expires, run release logic for that seat in DB and invalidate seat map cache.
- [ ] **Option B:** Use a short-interval poller that checks Redis for keys about to expire, or use a separate “hold expiry” set (ZADD with score = expiry time) and ZRANGEBYSCORE to find expired holds and process in batch.
- [ ] Release logic: update seat state to AVAILABLE in DB using optimistic lock (or atomic UPDATE ... WHERE state = 'HELD' AND held_until < now()); invalidate `seatmap:flight:{flightId}` in Redis.

### 3. Consistency and Safety

- [ ] Ensure only one node processes a given seat’s expiry (e.g. single consumer for keyspace events, or leader election for poller).
- [ ] Idempotent release: if seat is already AVAILABLE or CONFIRMED, no-op.
- [ ] Do not release if seat was confirmed before TTL (check seat state or version before updating).

### 4. Fallback and Configuration

- [ ] Keep scheduler-based release (e.g. every 10–30 seconds) that runs DB query `state = 'HELD' AND held_until < now()` as primary or fallback.
- [ ] Make Redis-based expiry optional (feature flag or only when Redis is enabled).
- [ ] Document: with Redis TTL, expiry can be closer to 120s; with scheduler only, expiry may be up to one scheduler interval late.

## Acceptance Criteria

- When Redis TTL expiry is enabled, expired holds are released in DB and seat map cache is invalidated.
- No duplicate release (idempotent); no release of already CONFIRMED seats.
- When Redis is disabled or TTL feature is off, scheduler-based release still ensures seats become AVAILABLE after hold window.

## References

- [performance-optimization.md](../../performance-optimization.md) — Section 3 (Must-have 4), Section 5 (Redis vs Elasticsearch).
