# Task: Seat Map Cache (Redis)

**Source:** [performance-optimization.md](../../performance-optimization.md) — Must-have 1

## Objective

Implement Redis-backed caching for seat map data so that seat map loads meet P95 < 1 second, support hundreds of concurrent users, and return near real-time availability.

## Problem Statement (exact)

- "Seat map data must be loaded quickly during peak usage. P95 should be less than 1 second."
- "The system must support hundreds of concurrent users."
- "Seat availability should be accurate and near real-time"

## Scope

- Add Redis as a dependency and configure connection (e.g. Lettuce with connection pooling).
- Cache per-flight seat map (or seat list + availability) in Redis.
- Invalidate or update cache on seat state changes (hold, confirm, cancel, expiry).
- Serve seat map reads from cache when present; fallback to DB and repopulate on miss.
- Use connection pooling and optional pipelining for high concurrency.

## Key Deliverables

### 1. Redis Setup

- [x] Add Spring Data Redis (or Lettuce) dependency to backend.
- [x] Configure Redis connection and connection pool in `application.yml`.
- [x] Add Redis to `docker-compose.yml` for local/dev.
- [x] Make Redis optional or feature-flagged so app runs without Redis when disabled.

### 2. Cache Design

- [x] Define cache key scheme (e.g. `seatmap:flight:{flightId}`).
- [x] Choose cache TTL (e.g. 5–30 seconds) and document rationale.
- [x] Serialize/deserialize seat map DTO (or entity summary) for Redis (JSON or binary).

### 3. Seat Map Service Integration

- [x] In seat map / flight seat API, check Redis first; on hit return cached data.
- [x] On cache miss: load from DB, write to Redis with TTL, then return.
- [x] Handle cache failures gracefully (fallback to DB only, log warning).

### 4. Cache Invalidation

- [x] On seat state change (HELD, CONFIRMED, CANCELLED, or expiry): invalidate or update the flight’s seat map cache.
- [x] Identify all code paths that change seat state and add invalidation (e.g. reserve, confirm, cancel, scheduler release).
- [x] Ensure invalidation runs after DB transaction commits (e.g. `@TransactionalEventListener(phase = AFTER_COMMIT)` or equivalent).

### 5. Performance and Operations

- [x] Use connection pooling; tune pool size for “hundreds of concurrent users.”
- [x] Optionally use Redis pipelining for batch get/set if needed.
- [x] Add metrics (cache hit/miss, latency) and document how to verify P95 < 1s (e.g. load test).

## Acceptance Criteria

- Seat map endpoint responds from cache when cache is warm; P95 latency target < 1 second under load.
- Cache is invalidated/updated when any seat on that flight changes state.
- Application continues to work (DB fallback) if Redis is unavailable.

## Implementation Notes

- **Cache key**: `seatmap:flight:{flightId}` (see `RedisSeatMapCacheConfig.CACHE_KEY_PREFIX`).
- **TTL**: `app.redis.seat-map-cache-ttl-seconds` (default 30s). Balances freshness vs load; 30s supports P95 < 1s under load.
- **Disable Redis**: Set `app.redis.seat-map-cache-enabled=false` (or use test profile); app falls back to DB-only.
- **Metrics** (when Redis cache enabled): `seatmap.cache.hits`, `seatmap.cache.misses`, `seatmap.cache.get.duration`, `seatmap.cache.puts`, `seatmap.cache.invalidations`, `seatmap.cache.errors`. Exposed via Actuator `/actuator/metrics`. Use load tests and P95 of `seatmap.cache.get.duration` to verify P95 < 1s.

## References

- [performance-optimization.md](../../performance-optimization.md) — Section 3 (Must-have 1), Section 4 summary table.
