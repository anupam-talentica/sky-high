# Task 004: Seat Management Module

## Objective
Implement the core seat management functionality including seat map retrieval, seat reservation, and state machine enforcement.

## Scope
- Implement seat map API
- Implement seat reservation with optimistic locking
- Implement seat state machine
- Configure caching for seat maps
- Handle concurrent seat reservations

## Key Deliverables

### 1. Seat Service Layer
- [ ] Create SeatService interface
- [ ] Create SeatServiceImpl with business logic
- [ ] Implement getAvailableSeats(flightId) method
- [ ] Implement reserveSeat(flightId, seatNumber, passengerId) method
- [ ] Implement releaseSeat(seatId) method
- [ ] Implement confirmSeat(seatId) method
- [ ] Implement cancelSeat(seatId) method

### 2. State Machine Implementation
- [ ] Define SeatState enum (AVAILABLE, HELD, CONFIRMED, CANCELLED)
- [ ] Implement state transition validation in Seat entity
- [ ] Valid transitions: AVAILABLE → HELD → CONFIRMED → CANCELLED
- [ ] Allow HELD → AVAILABLE (expiration)
- [ ] Allow CANCELLED → AVAILABLE (reuse)
- [ ] Throw InvalidStateTransitionException for invalid transitions

### 3. Optimistic Locking
- [ ] Add @Version field to Seat entity
- [ ] Handle OptimisticLockException in service layer
- [ ] Throw SeatConflictException when concurrent update detected
- [ ] Implement retry logic for transient conflicts

### 4. Seat Controller
- [ ] Create SeatController with REST endpoints
- [ ] Implement GET /api/v1/flights/{id}/seat-map
- [ ] Implement POST /api/v1/flights/{id}/seats/{seat}/reserve
- [ ] Implement POST /api/v1/seats/{id}/release
- [ ] Implement POST /api/v1/seats/{id}/confirm
- [ ] Implement POST /api/v1/seats/{id}/cancel
- [ ] Add request validation and error handling

### 5. Caching Strategy
- [ ] Configure Caffeine cache in application.yml
- [ ] Create cache named "seatMaps" with 60-second TTL
- [ ] Add @Cacheable on getAvailableSeats method
- [ ] Add @CacheEvict on seat state change methods
- [ ] Invalidate cache for specific flight only

### 6. DTOs
- [ ] Create SeatMapResponseDTO
- [ ] Create SeatDTO with state, number, availability
- [ ] Create SeatReservationRequestDTO
- [ ] Create SeatReservationResponseDTO
- [ ] Add validation annotations (@NotNull, @Pattern)

### 7. Audit Logging
- [ ] Log all seat state transitions to audit_logs table
- [ ] Include old state, new state, timestamp, user ID
- [ ] Store as JSON for flexibility
- [ ] Implement async logging to avoid performance impact

## Dependencies
- Database schema for seats table
- JWT authentication configured
- Caffeine cache dependency added

## Success Criteria
- Seat map API returns correct seat availability
- Seat reservation works with 120-second hold timer
- Concurrent reservations are handled correctly with optimistic locking
- Invalid state transitions are rejected
- Cache improves performance for repeated seat map queries
- All state changes are logged to audit_logs

## Estimated Effort
High-level seat management implementation task

## References
- TRD.md Section 5.2: State Machine Enforcement
- TRD.md Section 7: Caching Strategy
- PRD.md Section 5: Seat Management
- database-patterns.mdc cursor rule
