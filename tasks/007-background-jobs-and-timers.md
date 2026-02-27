# Task 007: Background Jobs and Timers

## Objective
Implement scheduled tasks for seat expiration handling and other background processing requirements.

## Scope
- Implement seat expiration timer
- Implement waitlist processing trigger
- Configure Spring @Scheduled tasks
- Ensure reliability and performance

## Key Deliverables

### 1. Seat Expiration Scheduler
- [ ] Create SeatExpirationScheduler class
- [ ] Implement @Scheduled method running every 5 seconds
- [ ] Query database for expired seats: `held_until < NOW() AND state = 'HELD'`
- [ ] Transition expired seats to AVAILABLE
- [ ] Invalidate cache for affected flights
- [ ] Trigger waitlist processing for released seats
- [ ] Log expiration events

### 2. Scheduler Configuration
- [ ] Enable scheduling with @EnableScheduling
- [ ] Configure thread pool for scheduled tasks
- [ ] Set pool size to handle concurrent expirations
- [ ] Configure error handling for failed tasks
- [ ] Implement retry logic for transient failures

### 3. Database-Backed Timer State
- [ ] Store held_until timestamp in seats table
- [ ] Query based on timestamp (survives restarts)
- [ ] Use database indexes for efficient queries
- [ ] Handle timezone considerations (UTC)

### 4. Waitlist Processing Integration
- [ ] Call WaitlistService.processWaitlist() after seat release
- [ ] Handle multiple waitlist entries for same seat
- [ ] Ensure atomic processing (one passenger at a time)
- [ ] Log waitlist assignments

### 5. Performance Optimization
- [ ] Use batch updates for multiple seat expirations
- [ ] Limit query results to prevent overload
- [ ] Add monitoring for scheduler execution time
- [ ] Implement circuit breaker for database issues

### 6. Reliability Features
- [ ] Handle scheduler failures gracefully
- [ ] Implement distributed lock if scaling to multiple instances
- [ ] Add health check for scheduler status
- [ ] Alert on scheduler failures

### 7. Testing
- [ ] Unit tests for expiration logic
- [ ] Integration tests with test containers
- [ ] Test concurrent expirations
- [ ] Test scheduler recovery after failure
- [ ] Performance tests for 1000+ concurrent timers

## Dependencies
- Seat Management Module completed
- Waitlist Management Module completed
- Database schema with held_until column

## Success Criteria
- Seats expire exactly 120 seconds after reservation
- Expired seats are released within 5 seconds (scheduler interval)
- Waitlist is processed automatically after seat release
- Scheduler handles 1000+ concurrent timers
- Timer precision: ±2 seconds acceptable
- Scheduler survives application restarts
- No memory leaks or performance degradation

## Estimated Effort
High-level background jobs implementation task

## References
- TRD.md Section 8: Background Jobs & Timers
- PRD.md Section 5.2: Seat Hold Timer
