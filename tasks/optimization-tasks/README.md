# Optimization Tasks (Redis-Based)

This folder contains implementation tasks for the **Redis-based** optimizations described in [performance-optimization.md](../../performance-optimization.md).

## Task List

| Task | Description | Priority |
|------|-------------|----------|
| [000-redis-setup.md](000-redis-setup.md) | Redis as Docker container; add to docker-compose and document in README | Foundation |
| [001-seat-map-cache-redis.md](001-seat-map-cache-redis.md) | Seat map cache in Redis — P95 < 1s, hundreds of users, near real-time | Must-have |
| [002-abuse-bot-detection-rate-limiting-redis.md](002-abuse-bot-detection-rate-limiting-redis.md) | Abuse/bot detection and temporary blocking with Redis rate limiting | Must-have |
| [003-distributed-seat-lock-redis.md](003-distributed-seat-lock-redis.md) | Optional Redis distributed lock for conflict-free seat assignment (multi-node) | Optional |
| [004-seat-hold-expiry-redis-ttl.md](004-seat-hold-expiry-redis-ttl.md) | Optional Redis TTL (and keyspace notifications) for seat hold expiry | Optional |

## Suggested Order

1. **000** — Redis setup (Docker container, docker-compose, README).
2. **001** — Seat map cache (foundation for latency and throughput).
3. **002** — Abuse detection and blocking (depends on Redis being available; can share Redis with 001).
4. **003** — Add when running multiple instances and DB contention is high.
5. **004** — Add when more precise hold expiry (closer to 120s) is required; keep scheduler as fallback.

## Dependencies

- Task 000 adds Redis to `docker-compose.yml` and documents it in the root README.
- Tasks 001 and 002 require Redis (from 000) and backend integration.
- Task 003 assumes Redis is already in use (e.g. from 001/002).
- Task 004 assumes Redis and seat map cache (001) exist for cache invalidation on release.

## Reference

- [performance-optimization.md](../../performance-optimization.md) — Full mapping of problem-statement requirements, technology grouping, and five must-have optimizations.
