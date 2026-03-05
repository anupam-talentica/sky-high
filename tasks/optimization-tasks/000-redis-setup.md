# Task 000: Redis Setup (Docker)

**Source:** [performance-optimization.md](../../performance-optimization.md) — Foundation for Redis-based optimizations

## Objective

Add Redis as a Docker container to the project so that downstream optimization tasks (seat map cache, abuse detection, optional distributed lock, hold expiry) can use it. The application should be able to run with or without Redis (optional/fail-safe).

## Scope

- Add a Redis service to `docker-compose.yml`.
- Expose Redis on a standard port for local development.
- Optionally configure backend to connect to Redis when available (or defer to tasks 001+).
- Document Redis in the root README (architecture, env vars, how to start/stop).

## Key Deliverables

### 1. Docker Compose — Redis Service

- [ ] Add a `redis` service to `docker-compose.yml` using an official Redis image (e.g. `redis:7-alpine`).
- [ ] Set container name (e.g. `skyhigh-redis`).
- [ ] Expose port `6379` for host access (e.g. `"6379:6379"`).
- [ ] Attach the service to the same network as backend (e.g. `skyhigh-network`).
- [ ] Add a healthcheck so other services can depend on Redis being ready (e.g. `redis-cli ping`).
- [ ] Use a volume for Redis data persistence (optional): e.g. `redis-data` so data survives container restarts.
- [ ] Set `restart: unless-stopped` for long-running dev.

### 2. Service Dependencies (Optional)

- [ ] If backend will use Redis from the start: add `depends_on: redis: condition: service_healthy` to the backend service.
- [ ] If Redis is optional: do not make backend depend on Redis in compose so the stack can run without Redis; backend connects only when Redis is configured and available.

### 3. Environment Configuration

- [ ] Document Redis connection settings for the backend (e.g. `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD` if used).
- [ ] In `docker-compose.yml`, ensure backend receives Redis URL/host when Redis is used (e.g. `REDIS_HOST=redis`, `REDIS_PORT=6379`).
- [ ] Add these variables to the root README under Configuration / Environment Variables.

### 4. Root README Updates

- [ ] In **Architecture**, add Redis (e.g. "Caching / session: Redis 7" or "Optional: Redis for cache and rate limiting").
- [ ] In **Getting Started** (Docker Compose), mention that Redis is included and which port it uses (6379).
- [ ] In **Configuration**, add a table row for Redis-related env vars (e.g. `REDIS_HOST`, `REDIS_PORT`, optional `REDIS_PASSWORD`).
- [ ] In **Troubleshooting**, add a short "Redis" subsection (e.g. how to check Redis is up: `docker-compose ps redis`, `docker-compose exec redis redis-cli ping`).

### 5. Local Verification

- [ ] Run `docker-compose up -d` and verify Redis starts: `docker-compose ps redis`.
- [ ] Verify health: `docker-compose exec redis redis-cli ping` returns `PONG`.
- [ ] Optionally connect from host: `redis-cli -h localhost -p 6379 ping`.

## Acceptance Criteria

- `docker-compose up -d` starts Redis along with other services.
- Redis is reachable from the host on port 6379 and from the backend container via hostname `redis`.
- Root README describes Redis in architecture, configuration, and troubleshooting.
- Backend can start without Redis (no hard dependency) if Redis is not configured or not running.

## References

- [performance-optimization.md](../../performance-optimization.md)
- [001-seat-map-cache-redis.md](001-seat-map-cache-redis.md) — First consumer of Redis
- [002-abuse-bot-detection-rate-limiting-redis.md](002-abuse-bot-detection-rate-limiting-redis.md) — Second consumer of Redis
