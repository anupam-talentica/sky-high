# Task: Abuse / Bot Detection and Temporary Blocking (Redis)

**Source:** [performance-optimization.md](../../performance-optimization.md) — Must-have 2

## Objective

Use Redis to detect when a single source (e.g. IP or user/session) rapidly accesses many seat maps and to temporarily restrict or block further access.

## Problem Statement (exact)

- "Detect cases where a single source rapidly accesses multiple seat maps"
- "One source accessing 50 different seat maps within 2 seconds"
- "When detected: The system must restrict or block further access temporarily"

## Scope

- Count seat-map accesses per source in a time window (e.g. 2 seconds) using Redis.
- When count exceeds a threshold (e.g. 50 in 2 seconds), mark the source as blocked with a short block TTL.
- Before serving seat map (and optionally seat reserve) requests, check if the source is blocked; if so, return 429 (or equivalent) and do not process.
- Optionally record the event for audit (see Must-have 5; can be a separate task).

## Key Deliverables

### 1. Redis Setup (if not already done)

- [ ] Ensure Redis is available (see task 001-seat-map-cache-redis.md).
- [ ] Reuse same Redis instance for rate-limit and block keys.

### 2. Source Identification

- [ ] Define “source” (e.g. IP, or authenticated user ID, or session ID); document choice.
- [ ] Extract source from request (e.g. `X-Forwarded-For`, `X-Real-IP`, or security context) in a reusable way (filter or interceptor).

### 3. Rate Counting

- [ ] Implement sliding or fixed window in Redis (e.g. 2-second window).
- [ ] Options: Redis INCR + EXPIRE per key per window, or sorted set (ZADD + ZREMRANGEBYSCORE) for sliding window.
- [ ] Key scheme (e.g. `ratelimit:seatmap:{sourceId}` or `ratelimit:seatmap:{sourceId}:{windowStart}`).
- [ ] Increment count on each seat map access; ensure TTL on keys to avoid unbounded growth.

### 4. Threshold and Blocking

- [ ] Configure threshold (e.g. 50 requests in 2 seconds) and block duration (e.g. 60–300 seconds).
- [ ] When count exceeds threshold: set a block key (e.g. `block:seatmap:{sourceId}`) with block TTL.
- [ ] Atomic check-and-set where possible (e.g. Lua script or MULTI/EXEC) to avoid race between count check and block.

### 5. Enforcement

- [ ] Before serving seat map (and optionally seat reservation): check block key; if present, return 429 Too Many Requests (or 403) with optional Retry-After header.
- [ ] Integrate check in filter, interceptor, or service layer so all seat map (and optionally reserve) endpoints are protected.
- [ ] Do not increment rate counter when request is rejected (blocked).

### 6. Configuration and Observability

- [ ] Make window size, threshold, and block TTL configurable (e.g. `application.yml`).
- [ ] Log or emit metrics when a source is blocked (for audit and tuning).

## Acceptance Criteria

- A source that accesses more than the configured number of seat maps within the configured window is temporarily blocked.
- Blocked sources receive 429 (or equivalent) and cannot access seat map (and optionally reserve) until block expires.
- Rate counting uses Redis and survives restarts (block TTL ensures blocks eventually clear).

## References

- [performance-optimization.md](../../performance-optimization.md) — Section 3 (Must-have 2), Section 4 summary table.
