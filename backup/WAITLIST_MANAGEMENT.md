# Waitlist Management Module

## Overview

The Waitlist Management module provides functionality for passengers to join a queue for unavailable seats and automatically assigns seats when they become available, following a FIFO (First-In-First-Out) order.

## Architecture

### Components

1. **WaitlistService** - Business logic for waitlist operations
2. **WaitlistController** - REST API endpoints
3. **NotificationService** - Email notifications for seat assignments
4. **Event-Driven Integration** - Automatic waitlist processing via Spring Events

### Event-Driven Design

The module uses Spring's event publishing mechanism to decouple seat management from waitlist processing:

```
Seat Released → SeatReleasedEvent → WaitlistEventListener → WaitlistService.processWaitlist()
```

This approach:
- Avoids circular dependencies
- Enables async processing
- Maintains transaction boundaries
- Improves scalability

## Features

### 1. Join Waitlist

**Endpoint**: `POST /api/v1/flights/{flightId}/seats/{seatNumber}/waitlist`

**Request**:
```json
{
  "passengerId": "P123456"
}
```

**Response**:
```json
{
  "waitlistId": 1,
  "passengerId": "P123456",
  "flightId": "SK1234",
  "seatNumber": "12A",
  "position": 3,
  "status": "WAITING",
  "joinedAt": "2026-02-27T18:00:00",
  "message": "Successfully joined waitlist at position 3"
}
```

**Business Rules**:
- Seat must not be AVAILABLE (otherwise reserve directly)
- Passenger cannot join waitlist twice for same seat
- Position assigned based on FIFO order (created_at timestamp)

### 2. Leave Waitlist

**Endpoint**: `DELETE /api/v1/waitlist/{waitlistId}`

**Response**: `204 No Content`

**Business Rules**:
- Can only leave if status is WAITING
- Status changed to CANCELLED

### 3. Get Waitlist Position

**Endpoint**: `GET /api/v1/waitlist/{waitlistId}/position`

**Response**:
```json
{
  "waitlistId": 1,
  "flightId": "SK1234",
  "seatNumber": "12A",
  "position": 3,
  "totalWaiting": 5,
  "status": "WAITING",
  "message": "You are at position 3 with 5 total passengers waiting"
}
```

### 4. Get Passenger Waitlist

**Endpoint**: `GET /api/v1/passengers/{passengerId}/waitlist`

**Response**: Array of waitlist entries for the passenger

### 5. Automatic Seat Assignment

When a seat becomes AVAILABLE (released, expired, or cancelled):

1. **Event Published**: `SeatReleasedEvent` is published
2. **Listener Triggered**: `WaitlistEventListener` receives event
3. **Process Waitlist**: Next waiting passenger is identified (FIFO)
4. **Seat Assigned**: Seat transitioned to HELD state
5. **Timer Started**: 120-second hold timer begins
6. **Notification Sent**: Email sent to passenger
7. **Status Updated**: Waitlist entry marked as ASSIGNED

### 6. Waitlist Expiration

If passenger doesn't complete check-in within 120 seconds:

1. Waitlist entry marked as EXPIRED
2. Seat released back to AVAILABLE
3. Next passenger in queue assigned
4. Expiration notification sent

## State Machine

### Waitlist States

```
WAITING → ASSIGNED → (Check-in completed)
   ↓         ↓
CANCELLED  EXPIRED → WAITING (back to queue)
```

**State Transitions**:
- `WAITING`: Passenger is in queue
- `ASSIGNED`: Seat assigned, waiting for check-in
- `EXPIRED`: Assignment expired, seat reassigned
- `CANCELLED`: Passenger left waitlist

## Database Schema

```sql
CREATE TABLE waitlist (
    waitlist_id BIGSERIAL PRIMARY KEY,
    passenger_id VARCHAR(20) NOT NULL,
    flight_id VARCHAR(20) NOT NULL,
    seat_number VARCHAR(5) NOT NULL,
    position INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'WAITING',
    joined_at TIMESTAMP,
    notified_at TIMESTAMP,
    assigned_at TIMESTAMP,
    expired_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_waitlist_passenger FOREIGN KEY (passenger_id) 
        REFERENCES passengers(passenger_id),
    CONSTRAINT fk_waitlist_flight FOREIGN KEY (flight_id) 
        REFERENCES flights(flight_id)
);

CREATE INDEX idx_flight_seat_status ON waitlist(flight_id, seat_number, status);
CREATE INDEX idx_waitlist_position ON waitlist(position);
CREATE INDEX idx_waitlist_passenger ON waitlist(passenger_id);
```

## Notification Service

### Email Templates

#### Seat Assignment Notification
```
Subject: Seat 12A Available - Flight SK1234

Dear Passenger P123456,

Good news! A seat has become available for your waitlist request.

Flight: SK1234
Seat: 12A

Your seat has been reserved for the next 120 seconds. Please complete 
your check-in immediately to confirm your seat assignment.

If you do not complete check-in within 120 seconds, the seat will be 
assigned to the next passenger on the waitlist.

Thank you for choosing SkyHigh Airlines.
```

#### Expiration Notification
```
Subject: Seat Assignment Expired - Flight SK1234

Dear Passenger P123456,

Unfortunately, your seat assignment for the following flight has expired:

Flight: SK1234
Seat: 12A

The seat has been assigned to the next passenger on the waitlist as you 
did not complete check-in within the 120-second time limit.

You remain on the waitlist and will be notified if another seat becomes 
available.
```

### Implementation

**Current**: Mock email service (logs to console)
**Future**: AWS SES integration for production

```java
@Service
public class NotificationServiceImpl implements NotificationService {
    
    @Async
    public void sendSeatAssignmentNotification(String email, String passengerId, 
                                              String flightId, String seatNumber) {
        // Send email via AWS SES
    }
}
```

## Integration Points

### 1. Seat Service Integration

Seat release triggers waitlist processing via events:

```java
// In SeatServiceImpl
eventPublisher.publishEvent(new SeatReleasedEvent(this, flightId, seatNumber));
```

### 2. Check-In Service Integration

When check-in is completed:
- Seat transitioned from HELD to CONFIRMED
- Waitlist entry remains in ASSIGNED state (historical record)

When check-in expires:
- Seat released back to AVAILABLE
- Waitlist entry marked as EXPIRED
- Next passenger in queue assigned

### 3. Scheduler Integration

`SeatExpirationScheduler` publishes `SeatReleasedEvent` when seats expire, triggering automatic waitlist processing.

## Error Handling

### Exception Classes

1. **WaitlistNotFoundException**: Waitlist entry not found
2. **WaitlistAlreadyExistsException**: Passenger already on waitlist
3. **NotificationFailedException**: Email notification failed

### Global Exception Handler

```java
@ExceptionHandler(WaitlistNotFoundException.class)
public ResponseEntity<ErrorResponse> handleWaitlistNotFound() {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
}

@ExceptionHandler(WaitlistAlreadyExistsException.class)
public ResponseEntity<ErrorResponse> handleWaitlistAlreadyExists() {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
}
```

## Testing

### Unit Tests

**Coverage**: 15 test cases covering:
- Join waitlist (success, seat available, already on waitlist)
- Leave waitlist (success, not found, invalid status)
- Process waitlist (assign seat, no waiting passengers, seat not available)
- Get position (success, not found)
- Get passenger waitlist
- Expire assignment (success, not assigned)

**Test Results**: All 15 tests passing ✓

```bash
mvn test -Dtest=WaitlistServiceTest
```

### Test Coverage

```
Tests run: 15, Failures: 0, Errors: 0, Skipped: 0
```

## Performance Considerations

### Optimizations

1. **Indexed Queries**: Composite index on (flight_id, seat_number, status)
2. **FIFO Order**: Position field for efficient queue management
3. **Async Notifications**: Email sending doesn't block main flow
4. **Event-Driven**: Decoupled processing for better scalability

### Scalability

- **Current**: Handles 100-500 concurrent users
- **Future**: Redis-backed queue for distributed systems

## Configuration

### Application Properties

```yaml
app:
  seat-hold-duration: 120  # Seconds to hold seat for waitlist assignment
```

## API Examples

### Join Waitlist

```bash
curl -X POST http://localhost:8080/api/v1/flights/SK1234/seats/12A/waitlist \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{"passengerId": "P123456"}'
```

### Leave Waitlist

```bash
curl -X DELETE http://localhost:8080/api/v1/waitlist/1 \
  -H "Authorization: Bearer <token>"
```

### Get Position

```bash
curl -X GET http://localhost:8080/api/v1/waitlist/1/position \
  -H "Authorization: Bearer <token>"
```

### Get Passenger Waitlist

```bash
curl -X GET http://localhost:8080/api/v1/passengers/P123456/waitlist \
  -H "Authorization: Bearer <token>"
```

## Monitoring

### Audit Logging

All state transitions are logged:
- Waitlist entry created (WAITING)
- Seat assigned (ASSIGNED)
- Assignment expired (EXPIRED)
- Waitlist cancelled (CANCELLED)

### Metrics

- Number of waitlist entries per flight
- Average wait time
- Assignment success rate
- Expiration rate

## Future Enhancements

1. **Priority Queue**: VIP passengers get priority
2. **Seat Preferences**: Match passenger preferences (aisle, window)
3. **Multi-Seat Waitlist**: Join waitlist for multiple seats
4. **SMS Notifications**: In addition to email
5. **Real-Time Updates**: WebSocket notifications
6. **Waitlist Analytics**: Dashboard for airline staff

## Dependencies

- Spring Boot 3.2+
- Spring Data JPA
- Spring Events
- PostgreSQL 15
- Lombok
- JUnit 5 + Mockito

## Related Modules

- [Seat Management](SEAT_MANAGEMENT.md)
- [Check-In Management](CHECKIN_MANAGEMENT.md)
- [Authentication & Security](AUTHENTICATION.md)

## Status

✅ **Completed**
- All core features implemented
- Unit tests passing (15/15)
- Integration with seat management
- Event-driven architecture
- Notification service (mock)

## References

- [TRD.md Section 8.2](../TRD.md#82-waitlist-processing)
- [Task 006](../tasks/006-waitlist-management.md)
