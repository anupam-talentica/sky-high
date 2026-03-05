# Background Jobs & Timers

## Overview

This document describes the implementation of background jobs and scheduled tasks in the SkyHigh Core system, specifically focusing on the seat expiration timer and waitlist processing.

## Architecture

### Components

1. **SeatExpirationScheduler** - Main scheduler component that runs periodically to release expired seats
2. **SchedulerConfig** - Configuration for thread pool and scheduling infrastructure
3. **SchedulerHealthIndicator** - Health check endpoint for monitoring scheduler status
4. **Event System** - Asynchronous event-driven architecture for triggering waitlist processing

### Flow Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                  Seat Expiration Flow                        │
└─────────────────────────────────────────────────────────────┘

Every 5 seconds:
    │
    ├─> SeatExpirationScheduler.releaseExpiredSeats()
    │       │
    │       ├─> Query: findExpiredHeldSeats(now)
    │       │       WHERE held_until < NOW() AND state = 'HELD'
    │       │
    │       ├─> Process batch (max 100 seats)
    │       │       │
    │       │       ├─> For each expired seat:
    │       │       │       ├─> Validate state (must be HELD)
    │       │       │       ├─> Validate expiration time
    │       │       │       ├─> Transition: HELD → AVAILABLE
    │       │       │       ├─> Clear held_by and held_until
    │       │       │       ├─> Save to database
    │       │       │       ├─> Log audit trail
    │       │       │       └─> Publish SeatReleasedEvent
    │       │       │
    │       │       └─> Handle errors gracefully (continue processing)
    │       │
    │       ├─> Clear cache (seatMaps)
    │       ├─> Update metrics (Micrometer)
    │       └─> Update health status
    │
    └─> WaitlistEventListener.handleSeatReleased()
            │
            ├─> Process waitlist asynchronously
            ├─> Find next waiting passenger (FIFO)
            ├─> Assign seat to passenger
            └─> Send notification
```

## Implementation Details

### 1. Seat Expiration Scheduler

**Location**: `com.skyhigh.scheduler.SeatExpirationScheduler`

**Key Features**:
- Runs every 5 seconds (configurable via `app.seat-release-job-interval`)
- Processes up to 100 expired seats per run (configurable via `app.seat-expiration-batch-size`)
- Implements retry logic with exponential backoff (3 attempts by default)
- Publishes events for asynchronous waitlist processing
- Tracks metrics using Micrometer
- Provides health check status

**Configuration**:
```yaml
app:
  seat-release-job-interval: 5000 # milliseconds (5 seconds)
  seat-expiration-batch-size: 100 # maximum seats per run
  seat-expiration-retry-attempts: 3 # retry attempts on failure
```

**Database Query**:
```sql
SELECT * FROM seats 
WHERE held_until < NOW() 
  AND state = 'HELD'
ORDER BY held_until ASC
LIMIT 100;
```

**Performance Characteristics**:
- **Timer Precision**: ±2 seconds (acceptable as per requirements)
- **Throughput**: Can handle 1000+ concurrent timers
- **Batch Processing**: Limits processing to prevent overload
- **Graceful Degradation**: Continues processing even if individual seats fail

### 2. Scheduler Configuration

**Location**: `com.skyhigh.config.SchedulerConfig`

**Thread Pool Settings**:
- **Pool Size**: 5 threads
- **Thread Name Prefix**: `scheduler-`
- **Await Termination**: 60 seconds
- **Wait for Tasks on Shutdown**: Yes
- **Rejected Execution Handler**: CallerRunsPolicy

### 3. Health Monitoring

**Location**: `com.skyhigh.health.SchedulerHealthIndicator`

**Health Check Endpoint**: `/actuator/health`

**Health Status Criteria**:
- ✅ **Healthy**: Scheduler running successfully within expected interval
- ❌ **Unhealthy**: Scheduler failed or hasn't run in 3x the expected interval

**Response Example**:
```json
{
  "status": "UP",
  "components": {
    "schedulerHealthIndicator": {
      "status": "UP",
      "details": {
        "scheduler": "SeatExpirationScheduler",
        "status": "healthy",
        "lastSuccessfulRun": "2026-02-27 14:30:45",
        "lastRunReleasedCount": 5,
        "secondsSinceLastRun": 3
      }
    }
  }
}
```

### 4. Event-Driven Waitlist Processing

**Event**: `SeatReleasedEvent`
**Listener**: `WaitlistEventListener`

**Flow**:
1. Scheduler publishes `SeatReleasedEvent` after releasing a seat
2. Event listener processes asynchronously (after transaction commit)
3. Waitlist service finds next waiting passenger
4. Seat is assigned to passenger (HELD state for 120 seconds)
5. Notification is sent to passenger

**Benefits**:
- **Decoupling**: Scheduler doesn't directly depend on waitlist service
- **Async Processing**: Doesn't block scheduler execution
- **Transaction Safety**: Event processed after seat release is committed
- **Reliability**: Event listener has its own error handling

## Metrics & Monitoring

### Micrometer Metrics

**Counter Metrics**:
- `scheduler.seats.expired` - Total number of seats expired
- `scheduler.seats.expiration.failed` - Number of failed expiration attempts

**Timer Metrics**:
- `scheduler.seats.expiration.duration` - Time taken to process expirations

**Accessing Metrics**:
```bash
# Prometheus format
curl http://localhost:8080/actuator/prometheus | grep scheduler

# JSON format
curl http://localhost:8080/actuator/metrics/scheduler.seats.expired
```

### CloudWatch Integration

Metrics are automatically exported to CloudWatch when enabled:

```yaml
management:
  metrics:
    export:
      cloudwatch:
        enabled: true
        namespace: SkyHighCore
        batch-size: 20
```

**CloudWatch Metrics**:
- `scheduler_seats_expired_total`
- `scheduler_seats_expiration_failed_total`
- `scheduler_seats_expiration_duration_seconds`

### Logging

**Log Levels**:
- `DEBUG`: Scheduler start/stop, no expired seats found
- `INFO`: Seats released, waitlist triggered, successful runs
- `WARN`: Retry attempts, cache clear failures
- `ERROR`: Critical failures, database errors

**Example Logs**:
```
2026-02-27 14:30:45 - Starting scheduled task: Release expired seats
2026-02-27 14:30:45 - Found 5 expired seats, processing batch of 5
2026-02-27 14:30:45 - Released expired seat: 12A for flight SK1234 (previously held by P123456)
2026-02-27 14:30:45 - Published SeatReleasedEvent for seat 12A on flight SK1234
2026-02-27 14:30:45 - Successfully released 5 expired seats
```

## Error Handling & Reliability

### Retry Logic

**Strategy**: Exponential backoff with 3 attempts

```
Attempt 1: Immediate
Attempt 2: Wait 1 second
Attempt 3: Wait 2 seconds
```

**Failure Handling**:
- Individual seat failures don't stop batch processing
- Failed seats are logged and counted in metrics
- Scheduler continues with next seat in batch

### Database-Backed Timer State

**Advantages**:
- Survives application restarts
- No in-memory state to lose
- Distributed system friendly (multiple instances can run safely)

**Implementation**:
- `held_until` timestamp stored in database
- Query-based expiration check
- Optimistic locking prevents race conditions

### Cache Invalidation

**Strategy**: Clear entire `seatMaps` cache after releasing seats

**Fallback**: If cache clear fails, log warning but continue processing

**Rationale**: Ensures seat map reflects latest state immediately

## Performance Optimization

### Batch Processing

**Limit**: 100 seats per run (configurable)

**Rationale**:
- Prevents overwhelming the system with too many updates
- Ensures scheduler completes within reasonable time
- Allows other scheduled tasks to run

**Scaling**: If more than 100 seats expire, they'll be processed in next run (5 seconds later)

### Database Indexes

**Required Indexes**:
```sql
CREATE INDEX idx_held_until ON seats(held_until);
CREATE INDEX idx_flight_state ON seats(flight_id, state);
```

**Query Performance**:
- Index on `held_until` makes expiration query fast
- Index on `flight_id, state` optimizes seat map queries

### Connection Pooling

**HikariCP Settings**:
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
```

**Scheduler Impact**: Uses 1-2 connections during execution

## Testing

### Unit Tests

**Location**: `com.skyhigh.scheduler.SeatExpirationSchedulerTest`

**Coverage**:
- ✅ No expired seats scenario
- ✅ Multiple expired seats
- ✅ Seat not in HELD state (skip)
- ✅ Seat not expired (skip)
- ✅ Exception handling (continue processing)
- ✅ Metrics increment
- ✅ Batch size limit
- ✅ Health status
- ✅ Cache invalidation

### Integration Tests

**Location**: `com.skyhigh.scheduler.SeatExpirationSchedulerIntegrationTest`

**Coverage**:
- ✅ Real database integration
- ✅ Multiple expired seats release
- ✅ Concurrent expiration handling
- ✅ Health status after run
- ✅ Performance test (1000 seats)

**Running Tests**:
```bash
# Unit tests only
mvn test -Dtest=SeatExpirationSchedulerTest

# Integration tests
mvn test -Dtest=SeatExpirationSchedulerIntegrationTest

# All scheduler tests
mvn test -Dtest=*Scheduler*Test

# With coverage
mvn clean test jacoco:report
```

## Deployment Considerations

### Single Instance Deployment (MVP)

**Current Setup**: Single EC2 instance with Docker

**Scheduler Behavior**:
- One scheduler instance running
- No coordination needed
- Simple and reliable

### Multi-Instance Deployment (Future)

**Challenge**: Multiple schedulers processing same seats

**Solutions**:

1. **Database-Level Locking** (Recommended for MVP → Phase 2)
   ```sql
   SELECT * FROM seats 
   WHERE held_until < NOW() AND state = 'HELD'
   FOR UPDATE SKIP LOCKED
   LIMIT 100;
   ```

2. **Distributed Lock with Redis** (Phase 4+)
   ```java
   @Scheduled(fixedDelay = 5000)
   @SchedulerLock(name = "seatExpirationScheduler", 
                  lockAtMostFor = "10s", 
                  lockAtLeastFor = "3s")
   public void releaseExpiredSeats() { ... }
   ```

3. **Leader Election** (Phase 4+)
   - Use Spring Cloud or Kubernetes leader election
   - Only leader instance runs scheduler

### Monitoring & Alerts

**Recommended Alerts**:

1. **Scheduler Not Running**
   - Condition: `secondsSinceLastRun > 30`
   - Action: Page on-call engineer

2. **High Failure Rate**
   - Condition: `scheduler.seats.expiration.failed > 10 per minute`
   - Action: Alert operations team

3. **Slow Processing**
   - Condition: `scheduler.seats.expiration.duration > 5 seconds`
   - Action: Investigate performance

**CloudWatch Alarms**:
```bash
aws cloudwatch put-metric-alarm \
  --alarm-name scheduler-not-running \
  --alarm-description "Scheduler hasn't run in 30 seconds" \
  --metric-name scheduler_last_run_seconds \
  --namespace SkyHighCore \
  --statistic Maximum \
  --period 60 \
  --threshold 30 \
  --comparison-operator GreaterThanThreshold
```

## Troubleshooting

### Scheduler Not Running

**Symptoms**: Health check shows unhealthy, no logs

**Checks**:
1. Verify `@EnableScheduling` is present in application class
2. Check scheduler thread pool status in logs
3. Verify database connectivity
4. Check for application errors in logs

**Resolution**:
```bash
# Check health endpoint
curl http://localhost:8080/actuator/health

# Check scheduler metrics
curl http://localhost:8080/actuator/metrics/scheduler.seats.expired

# Check application logs
docker logs skyhigh-backend | grep -i scheduler
```

### Seats Not Expiring

**Symptoms**: Seats remain in HELD state past expiration time

**Checks**:
1. Verify scheduler is running (health check)
2. Check database query returns expired seats
3. Verify timezone configuration (should be UTC)
4. Check for database locks or deadlocks

**Debug Query**:
```sql
-- Find expired seats that should be released
SELECT seat_id, flight_id, seat_number, state, held_until, 
       NOW() as current_time,
       EXTRACT(EPOCH FROM (NOW() - held_until)) as seconds_expired
FROM seats
WHERE held_until < NOW() 
  AND state = 'HELD'
ORDER BY held_until ASC;
```

### High CPU Usage

**Symptoms**: Scheduler consuming excessive CPU

**Possible Causes**:
1. Too many expired seats (batch size too large)
2. Scheduler interval too short
3. Database query not using indexes

**Resolution**:
1. Increase `app.seat-release-job-interval` (e.g., 10000ms)
2. Decrease `app.seat-expiration-batch-size` (e.g., 50)
3. Verify database indexes exist

### Memory Leaks

**Symptoms**: Memory usage grows over time

**Checks**:
1. Verify cache is being cleared properly
2. Check for event listener memory leaks
3. Monitor thread pool size

**Resolution**:
```bash
# Enable heap dump on OOM
java -XX:+HeapDumpOnOutOfMemoryError \
     -XX:HeapDumpPath=/tmp/heapdump.hprof \
     -jar skyhigh-core.jar

# Monitor memory usage
curl http://localhost:8080/actuator/metrics/jvm.memory.used
```

## Best Practices

### Configuration

✅ **DO**:
- Use environment variables for configuration
- Set appropriate batch sizes based on load
- Enable health checks
- Configure retry logic

❌ **DON'T**:
- Hardcode configuration values
- Set batch size too large (> 500)
- Disable health checks in production
- Ignore retry failures

### Monitoring

✅ **DO**:
- Monitor scheduler health continuously
- Set up alerts for failures
- Track metrics over time
- Review logs regularly

❌ **DON'T**:
- Rely only on logs
- Ignore health check failures
- Skip metric collection
- Disable alerts

### Testing

✅ **DO**:
- Test with realistic data volumes
- Test concurrent scenarios
- Test failure scenarios
- Test performance under load

❌ **DON'T**:
- Test only happy path
- Skip integration tests
- Ignore performance tests
- Test with small datasets only

## Future Enhancements

### Phase 2: Separate Database
- Optimize queries with database-specific features
- Use PostgreSQL advisory locks for coordination

### Phase 3: Load Balancer
- No changes needed (scheduler works with multiple instances)

### Phase 4: High Availability
- Implement distributed locking with Redis
- Add leader election for scheduler coordination
- Scale scheduler thread pool based on load
- Implement circuit breaker for database failures

## References

- [Spring Scheduling Documentation](https://docs.spring.io/spring-framework/docs/current/reference/html/integration.html#scheduling)
- [Micrometer Documentation](https://micrometer.io/docs)
- [Spring Boot Actuator](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)
- [TRD.md Section 8: Background Jobs & Timers](../TRD.md#8-background-jobs--timers)
- [PRD.md Section 5.2: Seat Hold Timer](../PRD.md#52-seat-hold-timer)
