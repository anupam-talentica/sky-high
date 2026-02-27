# Task 005: Check-In Module

## Objective
Implement the complete check-in workflow including baggage handling, payment processing, and check-in confirmation.

## Scope
- Implement check-in creation and management
- Integrate baggage weight validation
- Integrate payment processing
- Implement check-in confirmation and cancellation
- Handle check-in state transitions

## Key Deliverables

### 1. Check-In Service Layer
- [ ] Create CheckInService interface
- [ ] Create CheckInServiceImpl with business logic
- [ ] Implement startCheckIn(passengerId, flightId) method
- [ ] Implement addBaggage(checkInId, baggageDetails) method
- [ ] Implement processPayment(checkInId, paymentDetails) method
- [ ] Implement confirmCheckIn(checkInId) method
- [ ] Implement cancelCheckIn(checkInId) method

### 2. Check-In State Machine
- [ ] Define CheckInStatus enum (INITIATED, BAGGAGE_ADDED, PAYMENT_COMPLETED, COMPLETED, CANCELLED)
- [ ] Implement state transition validation
- [ ] Valid transitions: INITIATED → BAGGAGE_ADDED → PAYMENT_COMPLETED → COMPLETED
- [ ] Allow cancellation from any state
- [ ] Throw InvalidStateTransitionException for invalid transitions

### 3. Baggage Service Integration
- [ ] Create BaggageService interface
- [ ] Create mock WeightService implementation
- [ ] Validate baggage weight (max 25 kg)
- [ ] Calculate excess weight and fees
- [ ] Store baggage details in baggage table
- [ ] Link baggage to check-in record

### 4. Payment Service Integration
- [ ] Create PaymentService interface
- [ ] Create mock payment processor
- [ ] Process baggage fee payments
- [ ] Generate mock transaction ID
- [ ] Handle payment success/failure
- [ ] Store payment details in check-in record

### 5. Check-In Controller
- [ ] Create CheckInController with REST endpoints
- [ ] Implement POST /api/v1/check-ins (start check-in)
- [ ] Implement POST /api/v1/check-ins/{id}/baggage
- [ ] Implement POST /api/v1/check-ins/{id}/payment
- [ ] Implement POST /api/v1/check-ins/{id}/confirm
- [ ] Implement POST /api/v1/check-ins/{id}/cancel
- [ ] Implement GET /api/v1/check-ins/{id} (get check-in details)

### 6. DTOs
- [ ] Create CheckInRequestDTO
- [ ] Create CheckInResponseDTO
- [ ] Create BaggageDetailsDTO
- [ ] Create PaymentRequestDTO
- [ ] Create PaymentResponseDTO
- [ ] Add validation annotations

### 7. Transaction Management
- [ ] Ensure check-in confirmation is transactional
- [ ] Update seat state to CONFIRMED
- [ ] Update check-in status to COMPLETED
- [ ] Set completion timestamp
- [ ] Rollback on failure

### 8. Integration with Seat Management
- [ ] Reserve seat when check-in starts
- [ ] Confirm seat when check-in completes
- [ ] Release seat when check-in is cancelled
- [ ] Handle seat expiration during check-in

## Dependencies
- Seat Management Module completed
- Database schema for check_ins and baggage tables
- JWT authentication configured

## Success Criteria
- Users can start check-in and reserve seat
- Baggage details are validated and stored
- Payment processing works (mock)
- Check-in confirmation updates seat to CONFIRMED
- Check-in cancellation releases seat
- All state transitions are validated
- Transactions maintain data consistency

## Estimated Effort
High-level check-in implementation task

## References
- TRD.md Section 9: External Services
- PRD.md Section 6: Check-In Flow
- PRD.md Section 7: Baggage Management
