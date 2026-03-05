# Performance Optimization

This document maps performance-related requirements from the problem statement to implementation approaches and groups them by suggested technology (Redis vs search/audit store such as Elasticsearch). It proposes **five must-have optimizations**.

---

## 1. Requirements Sourced from Problem Statement

### Scaling & reliability

| # | Exact quote | Section |
|---|-------------|--------|
| 1 | "Scales reliably during check-in rushes" | 1. Business Scenario |
| 2 | "This behavior must work reliably even during high traffic" | 2.2 Time-Bound Seat Hold (2 Minutes) |
| 3 | "Seat assignment must remain correct regardless of request volume" | 2.3 Conflict-Free Seat Assignment |
| 4 | "No race condition should result in duplicate seat assignments" | 2.3 Conflict-Free Seat Assignment |
| 5 | "The system must remain consistent under concurrent usage" | 2.3 Conflict-Free Seat Assignment |

### Seat map performance

| # | Exact quote | Section |
|---|-------------|--------|
| 6 | "Seat map browsing is the most frequently used feature." | 2.7 High-Performance Seat Map Access |
| 7 | "Seat map data must be loaded quickly during peak usage. **P95 should be less than 1 second.**" | 2.7 High-Performance Seat Map Access |
| 8 | "The system must support **hundreds of concurrent users**." | 2.7 High-Performance Seat Map Access |
| 9 | "Seat availability should be **accurate and near real-time**" | 2.7 High-Performance Seat Map Access |

### Abuse & audit

| # | Exact quote | Section |
|---|-------------|--------|
| 10 | "Detect cases where a single source rapidly accesses multiple seat maps" | 2.8 Abuse & Bot Detection |
| 11 | "One source accessing **50 different seat maps within 2 seconds**" | 2.8 Abuse & Bot Detection (example) |
| 12 | "When detected: The system must **restrict or block further access temporarily**" | 2.8 Abuse & Bot Detection |
| 13 | "The event must be **recorded for audit and review**" | 2.8 Abuse & Bot Detection |

---

## 2. Grouping by Technology

### Redis-oriented (fast data, rate limiting, consistency)

- **Quotes 6, 7, 8, 9** → Seat map load speed, P95 &lt; 1s, hundreds of concurrent users, near real-time availability.
- **Quotes 10, 11, 12** → Detect rapid seat-map access and temporarily restrict/block (e.g. 50 maps in 2 seconds).
- **Quotes 2, 3, 4, 5** → Reliable behavior under high traffic and conflict-free, consistent seat assignment (e.g. atomic reserve, optional distributed locking if multi-node).

### Elasticsearch (or similar) – audit and review

- **Quote 13** → Events recorded for audit and review (search by source, time range, action type).

---

## 3. Five Must-Have Optimizations

### Must-have 1: Seat map cache (Redis)

**Problem statement (exact):**

- "Seat map data must be loaded quickly during peak usage. P95 should be less than 1 second."
- "The system must support hundreds of concurrent users."
- "Seat availability should be accurate and near real-time"

**Implementation (brief):**

- Cache per-flight seat map (or seat list + availability) in Redis with a short TTL (e.g. 5–30 seconds).
- On seat state change (hold, confirm, cancel, expiry), invalidate or update the cache for that flight.
- Serve seat map reads from Redis when present; fallback to DB and repopulate cache on miss.
- Use connection pooling and pipelining for Redis to handle hundreds of concurrent users and keep P95 under 1 second.

---

### Must-have 2: Abuse / bot detection and temporary blocking (Redis)

**Problem statement (exact):**

- "Detect cases where a single source rapidly accesses multiple seat maps"
- "One source accessing 50 different seat maps within 2 seconds"
- "When detected: The system must restrict or block further access temporarily"

**Implementation (brief):**

- Use Redis to count seat-map accesses per source (e.g. IP or user/session ID) in a sliding or fixed window (e.g. 2 seconds).
- If count exceeds a threshold (e.g. 50 in 2 seconds), mark the source as blocked in Redis with a short block TTL (e.g. 60–300 seconds).
- Before serving seat map (or seat reserve) requests, check the block key; if set, return 429 or equivalent and do not process.
- Optionally use a sorted set or sliding-window counter for accurate “last 2 seconds” semantics.

---

### Must-have 3: Conflict-free, consistent seat assignment under load (DB + optional Redis)

**Problem statement (exact):**

- "Seat assignment must remain correct regardless of request volume"
- "No race condition should result in duplicate seat assignments"
- "The system must remain consistent under concurrent usage"

**Implementation (brief):**

- Use **optimistic locking** in the database (e.g. `@Version` on seat entity) so only one transaction can commit a given seat state transition; retry on conflict.
- Alternatively or in addition, use a **single atomic DB operation** (e.g. `UPDATE seat SET state = 'HELD', version = version + 1 WHERE id = ? AND state = 'AVAILABLE' AND version = ?`) and interpret affected rows to avoid duplicate assignment.
- If the system is multi-node and DB contention is high, consider a **Redis-based lock per seat** (or per flight) for the critical section that performs the reserve; keep lock TTL short to avoid deadlocks.

---

### Must-have 4: Reliable seat hold expiry under high traffic (scheduler + DB, optional Redis)

**Problem statement (exact):**

- "This behavior must work reliably even during high traffic"
- "If the passenger does not complete check-in within the time window: The seat must automatically become AVAILABLE again"

**Implementation (brief):**

- Use a **scheduled job** (e.g. every 10–30 seconds) that runs a single DB query to release seats where `state = 'HELD'` and `held_until < now()`.
- Ensure the release logic uses the same concurrency controls as must-have 3 (optimistic lock or atomic update).
- Optionally use **Redis TTL + keyspace notifications** to trigger release per seat for more granular expiry; still persist final state in DB and invalidate seat map cache (must-have 1).

---

### Must-have 5: Audit events for abuse and review (Elasticsearch or DB)

**Problem statement (exact):**

- "The event must be recorded for audit and review"

**Implementation (brief):**

- When abuse is detected (must-have 2), write an **audit event** (source, timestamp, count, action, flight IDs if applicable) to a store that supports search and review.
- **Elasticsearch**: Index audit events by source, time range, and event type; provide dashboards or search API for “audit and review.”
- **Alternative**: Use the existing **DB audit log** with indexed columns (e.g. `source_id`, `event_type`, `created_at`) and simple query API; sufficient if volume is moderate and query patterns are simple. Choose Elasticsearch when you need full-text or complex search over large audit data.

---

## 4. Summary Table

| Must-have | Problem-statement focus | Primary tool | Purpose |
|-----------|-------------------------|-------------|---------|
| 1 | P95 &lt; 1s, hundreds of users, near real-time | **Redis** | Seat map cache + invalidation |
| 2 | Detect rapid access, block temporarily | **Redis** | Rate limit + block window |
| 3 | No duplicate assignment, consistent under load | **DB** (optional Redis lock) | Concurrency control |
| 4 | Reliable hold expiry during high traffic | **Scheduler + DB** (optional Redis TTL) | Time-bound hold release |
| 5 | Record for audit and review | **Elasticsearch** or **DB** | Audit event storage and search |

---

## 5. Redis vs Elasticsearch at a glance

- **Redis**: Must-haves 1 (seat map cache), 2 (abuse detection and blocking), and optionally 3 (distributed lock) and 4 (TTL for hold expiry). Focus: low latency, high throughput, counters, and short-lived state.
- **Elasticsearch**: Must-have 5 (audit and review). Focus: indexing and querying abuse and audit events for operators. Use when audit volume or query needs justify a dedicated search store.
