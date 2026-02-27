# Task 006: Waitlist Management

## Objective
Implement waitlist functionality allowing passengers to join a queue for unavailable seats and automatically assign seats when available.

## Scope
- Implement waitlist join/leave operations
- Implement FIFO queue management
- Implement automatic seat assignment from waitlist
- Integrate with notification service

## Key Deliverables

### 1. Waitlist Service Layer
- [ ] Create WaitlistService interface
- [ ] Create WaitlistServiceImpl with business logic
- [ ] Implement joinWaitlist(passengerId, flightId, seatNumber) method
- [ ] Implement leaveWaitlist(waitlistId) method
- [ ] Implement processWaitlist(flightId, seatNumber) method
- [ ] Implement getWaitlistPosition(waitlistId) method

### 2. Waitlist State Management
- [ ] Define WaitlistStatus enum (ACTIVE, ASSIGNED, EXPIRED, CANCELLED)
- [ ] Implement FIFO queue logic based on created_at timestamp
- [ ] Track waitlist position for each entry
- [ ] Handle waitlist expiration after seat assignment

### 3. Automatic Seat Assignment
- [ ] Trigger when seat becomes AVAILABLE
- [ ] Query waitlist for flight/seat in FIFO order
- [ ] Assign seat to next passenger in queue
- [ ] Transition seat to HELD state
- [ ] Set 120-second hold timer
- [ ] Update waitlist status to ASSIGNED
- [ ] Send notification to passenger

### 4. Waitlist Controller
- [ ] Create WaitlistController with REST endpoints
- [ ] Implement POST /api/v1/flights/{id}/seats/{seat}/waitlist
- [ ] Implement DELETE /api/v1/waitlist/{id}
- [ ] Implement GET /api/v1/waitlist/{id}/position
- [ ] Implement GET /api/v1/passengers/{id}/waitlist (user's waitlist entries)

### 5. DTOs
- [ ] Create WaitlistJoinRequestDTO
- [ ] Create WaitlistResponseDTO
- [ ] Create WaitlistPositionDTO
- [ ] Add validation annotations

### 6. Notification Service Integration
- [ ] Create NotificationService interface
- [ ] Implement email notification for seat assignment
- [ ] Use AWS SES for email delivery
- [ ] Create email templates
- [ ] Implement async notification processing
- [ ] Handle notification failures gracefully

### 7. Integration with Seat Management
- [ ] Call processWaitlist when seat released
- [ ] Call processWaitlist when seat cancelled
- [ ] Call processWaitlist when seat expires
- [ ] Ensure atomic seat assignment from waitlist

### 8. Waitlist Expiration
- [ ] Expire waitlist entry if passenger doesn't confirm within 120 seconds
- [ ] Move to next passenger in queue
- [ ] Update waitlist status to EXPIRED

## Dependencies
- Seat Management Module completed
- Database schema for waitlist table
- AWS SES configured for notifications

## Success Criteria
- Passengers can join waitlist for unavailable seats
- Waitlist maintains FIFO order
- Seats are automatically assigned when available
- Passengers receive email notifications
- Waitlist position is accurately tracked
- Expired assignments move to next passenger

## Estimated Effort
High-level waitlist implementation task

## References
- TRD.md Section 8.2: Waitlist Processing
- TRD.md Section 9.1.3: Notification Service
- PRD.md Section 9: Waitlist Management
