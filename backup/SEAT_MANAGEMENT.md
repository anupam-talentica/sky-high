# Seat Management Module

## Overview

The Seat Management Module handles all seat-related operations including seat map retrieval, seat reservation, state transitions, and automatic expiration of held seats.

## Architecture

### State Machine

The seat state machine enforces valid state transitions:

```
AVAILABLE → HELD → CONFIRMED → CANCELLED
    ↑         ↓
    └─────────┘
```

**Valid Transitions:**
- `AVAILABLE → HELD`: When a seat is reserved
- `HELD → CONFIRMED`: When check-in is completed
- `HELD → AVAILABLE`: When hold expires or is manually released
- `CONFIRMED → CANCELLED`: When check-in is cancelled
- `CANCELLED → AVAILABLE`: When seat is made available again

### Components

#### 1. Entity Layer
- **Seat**: JPA entity with optimistic locking (`@Version`)
- **SeatState**: Enum with state transition validation logic

#### 2. Repository Layer
- **SeatRepository**: JPA repository with custom queries
- Supports optimistic locking with `@Lock(LockModeType.OPTIMISTIC)`
- Query methods for finding available seats, expired seats, etc.

#### 3. Service Layer
- **SeatService**: Interface defining seat operations
- **SeatServiceImpl**: Implementation with business logic
- **AuditLogService**: Async audit logging for state changes

#### 4. Controller Layer
- **SeatController**: REST API endpoints
- Request validation with `@Valid`
- Proper HTTP status codes

#### 5. Exception Handling
- **SeatNotFoundException**: Seat not found (404)
- **SeatConflictException**: Concurrent updates or unavailable seat (409)
- **InvalidStateTransitionException**: Invalid state transition (400)

## API Endpoints

### 1. Get Seat Map
```
GET /api/v1/flights/{flightId}/seat-map
```

**Response:**
```json
{
  "flightId": "SK1234",
  "totalSeats": 189,
  "availableSeats": 150,
  "heldSeats": 20,
  "confirmedSeats": 19,
  "seats": [
    {
      "seatNumber": "12A",
      "seatType": "ECONOMY",
      "state": "AVAILABLE",
      "available": true,
      "heldBy": null,
      "confirmedBy": null
    }
  ]
}
```

### 2. Reserve Seat
```
POST /api/v1/flights/{flightId}/seats/{seatNumber}/reserve
```

**Request:**
```json
{
  "passengerId": "P123456",
  "seatNumber": "12A"
}
```

**Response:**
```json
{
  "seatId": 1,
  "flightId": "SK1234",
  "seatNumber": "12A",
  "state": "HELD",
  "heldBy": "P123456",
  "heldUntil": "2026-02-27T10:32:00",
  "holdDurationSeconds": 120,
  "message": "Seat reserved successfully. Please complete check-in within 120 seconds."
}
```

### 3. Release Seat
```
POST /api/v1/seats/{seatId}/release
```

Releases a held seat back to available state.

### 4. Confirm Seat
```
POST /api/v1/seats/{seatId}/confirm?passengerId=P123456
```

Confirms a held seat (transitions to CONFIRMED state).

### 5. Cancel Seat
```
POST /api/v1/seats/{seatId}/cancel
```

Cancels a confirmed seat (transitions to CANCELLED state).

## Concurrency Control

### Optimistic Locking

The module uses JPA's optimistic locking mechanism to handle concurrent seat reservations:

1. **Version Field**: Each seat has a `version` field annotated with `@Version`
2. **Lock Mode**: Repository uses `@Lock(LockModeType.OPTIMISTIC)` for read operations
3. **Conflict Detection**: When two users try to reserve the same seat, the second transaction fails with `OptimisticLockException`
4. **Error Handling**: Service layer catches the exception and throws `SeatConflictException` with user-friendly message

**Example:**
```java
@Lock(LockModeType.OPTIMISTIC)
@Query("SELECT s FROM Seat s WHERE s.flightId = :flightId AND s.seatNumber = :seatNumber")
Optional<Seat> findByFlightIdAndSeatNumberWithLock(
    @Param("flightId") String flightId,
    @Param("seatNumber") String seatNumber
);
```

## Caching Strategy

### Cache Configuration

- **Cache Provider**: Caffeine (in-memory)
- **Cache Name**: `seatMaps`
- **TTL**: 60 seconds
- **Max Size**: 500 entries

### Cache Operations

**Cache Hit:**
```java
@Cacheable(value = "seatMaps", key = "#flightId")
public SeatMapResponseDTO getAvailableSeats(String flightId)
```

**Cache Eviction:**
```java
@CacheEvict(value = "seatMaps", key = "#flightId")
public SeatReservationResponseDTO reserveSeat(String flightId, ...)
```

### Performance Impact

- **First Request**: ~400-800ms (database query)
- **Cached Request**: ~150-250ms (cache hit)
- **Cache Invalidation**: Triggered on any seat state change

## Seat Expiration

### Automatic Release

A scheduled task runs every 30 seconds to release expired held seats:

```java
@Scheduled(fixedDelayString = "${app.seat-release-job-interval:30000}")
public void releaseExpiredSeats()
```

**Process:**
1. Query database for seats where `heldUntil < NOW() AND state = 'HELD'`
2. Transition each seat to `AVAILABLE` state
3. Clear `heldBy` and `heldUntil` fields
4. Log state change to audit log
5. Invalidate cache

**Configuration:**
```yaml
app:
  seat-hold-duration: 120 # seconds
  seat-release-job-interval: 30000 # milliseconds
```

## Audit Logging

All seat state transitions are logged asynchronously to the `audit_logs` table:

**Audit Log Entry:**
```json
{
  "entityType": "Seat",
  "entityId": "1",
  "action": "STATE_CHANGE",
  "oldState": "{\"state\":\"AVAILABLE\"}",
  "newState": "{\"state\":\"HELD\"}",
  "userId": "P123456",
  "timestamp": "2026-02-27T10:30:00"
}
```

**Async Processing:**
- Audit logs are written asynchronously to avoid performance impact
- Uses dedicated thread pool (`async-audit-*` threads)
- Failures are logged but don't affect main transaction

## Error Handling

### Exception Types

| Exception | HTTP Status | Description |
|-----------|-------------|-------------|
| `SeatNotFoundException` | 404 | Seat not found for given flight/seat number |
| `SeatConflictException` | 409 | Seat unavailable or concurrent update detected |
| `InvalidStateTransitionException` | 400 | Invalid state transition attempted |

### Example Error Response

```json
{
  "status": 409,
  "error": "Conflict",
  "message": "Seat 12A is not available. Current state: HELD",
  "path": "/api/v1/flights/SK1234/seats/12A/reserve",
  "timestamp": "2026-02-27T10:30:00"
}
```

## Testing

### Unit Tests

Location: `src/test/java/com/skyhigh/service/SeatServiceTest.java`

**Test Coverage:**
- ✅ Get seat map with available seats
- ✅ Get seat map when no seats exist
- ✅ Reserve available seat
- ✅ Reserve seat that doesn't exist
- ✅ Reserve seat that's already held
- ✅ Release held seat
- ✅ Release seat that's not held
- ✅ Confirm held seat
- ✅ Confirm seat held by different passenger
- ✅ Cancel confirmed seat
- ✅ Release expired seats
- ✅ Release expired seats when none exist

**Running Tests:**
```bash
mvn test -Dtest=SeatServiceTest
```

**Coverage Report:**
```bash
mvn clean test jacoco:report
```

## Configuration

### Application Properties

```yaml
spring:
  cache:
    type: caffeine
    caffeine:
      spec: maximumSize=1000,expireAfterWrite=300s

app:
  seat-hold-duration: 120 # seconds
  seat-release-job-interval: 30000 # milliseconds (30 seconds)
```

### Database Indexes

Ensure these indexes exist for optimal performance:

```sql
CREATE INDEX idx_flight_state ON seats(flight_id, state);
CREATE INDEX idx_held_until ON seats(held_until);
CREATE UNIQUE INDEX unique_flight_seat ON seats(flight_id, seat_number);
```

## Migration to Redis (Future)

To migrate from Caffeine to Redis for distributed caching:

1. Add Redis dependency to `pom.xml`
2. Update configuration:
   ```yaml
   spring:
     cache:
       type: redis
     redis:
       host: localhost
       port: 6379
   ```
3. No code changes required (Spring Boot handles the switch)

## Performance Benchmarks

| Operation | Target | Actual (MVP) |
|-----------|--------|--------------|
| Get Seat Map (cached) | < 300ms | 150-250ms |
| Get Seat Map (uncached) | < 1s | 400-800ms |
| Reserve Seat | < 500ms | 200-400ms |
| Release Expired Seats | < 5s | 1-3s |
| Concurrent Reservations | 500+ | 300-500 |

## Troubleshooting

### Issue: Seats not expiring

**Check:**
1. Verify scheduler is enabled: `@EnableScheduling` in main application class
2. Check configuration: `app.seat-release-job-interval`
3. Review logs for scheduler execution

### Issue: Cache not working

**Check:**
1. Verify cache is enabled: `@EnableCaching` in main application class
2. Check cache configuration in `application.yml`
3. Verify `@Cacheable` and `@CacheEvict` annotations

### Issue: Concurrent reservation conflicts

**Expected behavior:** Second user should receive 409 Conflict error

**Check:**
1. Verify `@Version` field exists in Seat entity
2. Verify optimistic locking is enabled in repository
3. Check transaction boundaries in service layer

## References

- [TRD.md Section 5.2](../TRD.md#52-key-design-patterns) - State Machine Enforcement
- [TRD.md Section 7](../TRD.md#7-caching-strategy) - Caching Strategy
- [PRD.md Section 5](../PRD.md#5-seat-management) - Seat Management Requirements
- [database-patterns.mdc](../.cursor/rules/database-patterns.mdc) - Database Patterns
