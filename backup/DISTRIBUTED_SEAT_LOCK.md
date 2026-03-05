# Distributed Seat Lock (Redis)

When running **multiple backend instances**, enable the Redis-based distributed seat lock so only one node can assign a given seat at a time. This reinforces conflict-free behavior under high request volume.

## When to enable

- **More than one backend instance** (e.g. horizontal scaling behind a load balancer).
- **High DB contention** on seat reservation (many concurrent reserve requests for the same flight/seat).

When disabled (default) or when Redis is unavailable, reservation uses only **DB optimistic locking** (`@Version` on `Seat`). Reservation still works correctly; the distributed lock reduces cross-node contention and retries.

## Configuration

| Property | Default | Description |
|----------|---------|-------------|
| `app.redis.distributed-seat-lock-enabled` | `false` | Set to `true` to use Redis for seat locks. Requires Redis (`spring.data.redis.*`). |
| `app.redis.seat-lock-ttl-seconds` | `10` | Lock TTL (5–15s recommended). Crashed nodes do not hold the lock indefinitely. |

Environment variable: `REDIS_DISTRIBUTED_SEAT_LOCK_ENABLED=true` to enable.

## Behavior

- **Lock key:** `lock:seat:{flightId}:{seatNumber}` (per seat).
- **Acquire:** `SET key token NX PX ttl` with a unique UUID token.
- **Release:** Lua script deletes the key only if the value equals the token (only the holder can unlock).
- **Lock not acquired:** API returns **503 Service Unavailable** with `Retry-After: 3` so clients can retry.
- **Redis unavailable:** Implementation falls back to DB-only (no lock); reservation still proceeds.

DB optimistic locking remains the source of truth; the Redis lock reduces contention and retries across nodes.
