# Check-In Management Module

## Overview

The Check-In Management Module handles the complete check-in workflow including seat reservation, baggage handling, payment processing, and check-in confirmation.

## Architecture

### State Machine

The check-in state machine enforces valid state transitions:

```
PENDING → BAGGAGE_ADDED → PAYMENT_COMPLETED → COMPLETED
   ↓            ↓                  ↓
   └────────────┴──────────────────┴────→ CANCELLED
```

**Valid Transitions:**
- `PENDING → BAGGAGE_ADDED`: When baggage details are added
- `PENDING → CANCELLED`: When check-in is cancelled before baggage
- `BAGGAGE_ADDED → PAYMENT_COMPLETED`: When payment is processed
- `BAGGAGE_ADDED → CANCELLED`: When check-in is cancelled after baggage
- `PAYMENT_COMPLETED → COMPLETED`: When check-in is confirmed
- `PAYMENT_COMPLETED → CANCELLED`: When check-in is cancelled after payment
- `COMPLETED → CANCELLED`: Not allowed (completed check-ins cannot be cancelled)

### Components

#### 1. Entity Layer
- **CheckIn**: JPA entity tracking check-in workflow state
- **Baggage**: JPA entity storing baggage details and payment info
- **CheckInStatus**: Enum with state transition validation logic

#### 2. Repository Layer
- **CheckInRepository**: JPA repository for check-in records
- **BaggageRepository**: JPA repository for baggage records

#### 3. Service Layer
- **CheckInService**: Interface defining check-in operations
- **CheckInServiceImpl**: Implementation with business logic
- **BaggageService**: Interface for baggage operations
- **BaggageServiceImpl**: Implementation with weight validation
- **PaymentService**: Interface for payment processing
- **PaymentServiceImpl**: Mock payment processor
- **WeightService**: Mock weight validation service

#### 4. Controller Layer
- **CheckInController**: REST API endpoints for check-in workflow

#### 5. Exception Handling
- **CheckInNotFoundException**: Check-in not found (404)
- **InvalidCheckInStateException**: Invalid state transition (400)
- **BaggageNotFoundException**: Baggage not found (404)
- **PaymentFailedException**: Payment processing failed (402)

## API Endpoints

### 1. Start Check-In
```
POST /api/v1/check-ins
```

**Request:**
```json
{
  "passengerId": "P123456",
  "flightId": "SK1234",
  "seatNumber": "12A"
}
```

**Response:**
```json
{
  "checkInId": "CHK-12345678",
  "passengerId": "P123456",
  "flightId": "SK1234",
  "seatId": 1,
  "seatNumber": "12A",
  "status": "PENDING",
  "checkInTime": "2026-02-27T10:30:00",
  "createdAt": "2026-02-27T10:30:00",
  "updatedAt": "2026-02-27T10:30:00",
  "message": "Check-in started successfully. Please add baggage details."
}
```

### 2. Add Baggage
```
POST /api/v1/check-ins/{checkInId}/baggage
```

**Request:**
```json
{
  "weightKg": 22.5,
  "dimensions": "55x40x23",
  "baggageType": "CHECKED"
}
```

**Response:**
```json
{
  "baggageId": 1,
  "checkInId": "CHK-12345678",
  "weightKg": 22.5,
  "dimensions": "55x40x23",
  "baggageType": "CHECKED",
  "excessWeightKg": 0,
  "excessFee": 0,
  "paymentStatus": "PAID",
  "message": "No excess baggage fee"
}
```

**With Excess Weight:**
```json
{
  "baggageId": 1,
  "checkInId": "CHK-12345678",
  "weightKg": 30.0,
  "dimensions": "55x40x23",
  "baggageType": "CHECKED",
  "excessWeightKg": 5.0,
  "excessFee": 50.00,
  "paymentStatus": "PENDING",
  "message": "Excess baggage fee: $50.00"
}
```

### 3. Process Payment
```
POST /api/v1/check-ins/{checkInId}/payment
```

**Request:**
```json
{
  "amount": 50.00,
  "paymentMethod": "CARD",
  "cardNumber": "4111111111111111",
  "cardHolderName": "John Doe"
}
```

**Response:**
```json
{
  "transactionId": "TXN-ABC12345",
  "amount": 50.00,
  "status": "PAID",
  "message": "Payment processed successfully",
  "processedAt": "2026-02-27T10:31:00"
}
```

**No Payment Required:**
```json
{
  "transactionId": "NO-PAYMENT-REQUIRED",
  "amount": 0,
  "status": "PAID",
  "message": "No payment required",
  "processedAt": "2026-02-27T10:31:00"
}
```

### 4. Confirm Check-In
```
POST /api/v1/check-ins/{checkInId}/confirm
```

**Response:**
```json
{
  "checkInId": "CHK-12345678",
  "passengerId": "P123456",
  "flightId": "SK1234",
  "seatId": 1,
  "seatNumber": "12A",
  "status": "COMPLETED",
  "checkInTime": "2026-02-27T10:30:00",
  "completedAt": "2026-02-27T10:32:00",
  "createdAt": "2026-02-27T10:30:00",
  "updatedAt": "2026-02-27T10:32:00",
  "message": "Check-in completed successfully!"
}
```

### 5. Cancel Check-In
```
POST /api/v1/check-ins/{checkInId}/cancel
```

**Response:**
```json
{
  "checkInId": "CHK-12345678",
  "passengerId": "P123456",
  "flightId": "SK1234",
  "seatId": 1,
  "status": "CANCELLED",
  "checkInTime": "2026-02-27T10:30:00",
  "cancelledAt": "2026-02-27T10:31:30",
  "createdAt": "2026-02-27T10:30:00",
  "updatedAt": "2026-02-27T10:31:30",
  "message": "Check-in cancelled successfully. Seat has been released."
}
```

### 6. Get Check-In Details
```
GET /api/v1/check-ins/{checkInId}
```

**Response:**
```json
{
  "checkInId": "CHK-12345678",
  "passengerId": "P123456",
  "flightId": "SK1234",
  "seatId": 1,
  "status": "PENDING",
  "checkInTime": "2026-02-27T10:30:00",
  "createdAt": "2026-02-27T10:30:00",
  "updatedAt": "2026-02-27T10:30:00"
}
```

## Check-In Workflow

### Complete Flow

```
1. Start Check-In
   ├─ Validate passenger and flight
   ├─ Check for existing active check-in
   ├─ Reserve seat (calls SeatService)
   ├─ Create check-in record (status: PENDING)
   ├─ Start 120-second seat hold timer
   └─ Return check-in ID

2. Add Baggage
   ├─ Validate check-in is in PENDING state
   ├─ Calculate excess weight (max 25 kg)
   ├─ Calculate excess fee ($10 per kg)
   ├─ Store baggage details
   ├─ Transition to BAGGAGE_ADDED
   └─ Return baggage details with fee

3. Process Payment (if excess fee > 0)
   ├─ Validate check-in is in BAGGAGE_ADDED state
   ├─ Verify payment amount matches excess fee
   ├─ Process payment (mock)
   ├─ Update baggage payment status
   ├─ Transition to PAYMENT_COMPLETED
   └─ Return payment confirmation

4. Confirm Check-In
   ├─ Validate check-in is in PAYMENT_COMPLETED state
   ├─ Confirm seat (calls SeatService)
   ├─ Transition to COMPLETED
   ├─ Set completion timestamp
   └─ Return confirmation

5. Cancel Check-In (optional, any time)
   ├─ Validate check-in is not COMPLETED
   ├─ Release seat (calls SeatService)
   ├─ Transition to CANCELLED
   ├─ Set cancellation timestamp
   └─ Return cancellation confirmation
```

## Transaction Management

### Transactional Operations

All check-in operations that modify multiple entities are wrapped in transactions:

```java
@Transactional
public CheckInResponseDTO confirmCheckIn(String checkInId) {
    // 1. Update check-in status
    // 2. Confirm seat (updates seat state)
    // 3. Both operations commit or rollback together
}
```

**Key Transactions:**
- **Start Check-In**: Creates check-in + reserves seat
- **Confirm Check-In**: Updates check-in + confirms seat
- **Cancel Check-In**: Updates check-in + releases seat

### Rollback Scenarios

Transactions rollback automatically on:
- Database errors
- Constraint violations
- Service exceptions (SeatConflictException, PaymentFailedException, etc.)
- Runtime exceptions

## Integration with Seat Management

### Seat Reservation Flow

```
CheckInService.startCheckIn()
   ↓
SeatService.reserveSeat()
   ↓
Seat state: AVAILABLE → HELD
   ↓
120-second hold timer starts
```

### Seat Confirmation Flow

```
CheckInService.confirmCheckIn()
   ↓
SeatService.confirmSeat()
   ↓
Seat state: HELD → CONFIRMED
   ↓
Hold timer cancelled
```

### Seat Release Flow

```
CheckInService.cancelCheckIn()
   ↓
SeatService.releaseSeat()
   ↓
Seat state: HELD → AVAILABLE
   ↓
Seat available for other passengers
```

## Baggage Management

### Weight Validation

**Configuration:**
```yaml
app:
  baggage:
    max-weight: 25 # kg
    excess-fee-per-kg: 10 # USD
```

**Calculation:**
- **Allowed Weight**: 25 kg (configurable)
- **Excess Weight**: `actualWeight - maxWeight` (if positive)
- **Excess Fee**: `excessWeight × $10/kg`

**Example:**
- Weight: 30 kg
- Excess: 5 kg
- Fee: $50.00

### Baggage Types

- `CHECKED`: Standard checked baggage
- `CARRY_ON`: Carry-on baggage
- `SPECIAL`: Special baggage (sports equipment, musical instruments)

## Payment Processing

### Mock Payment Service

The payment service is mocked for MVP with configurable success rate:

**Configuration:**
```yaml
app:
  payment:
    mock-success-rate: 0.95 # 95% success rate
```

**Transaction ID Format:** `TXN-XXXXXXXX` (8 random uppercase characters)

**Payment Flow:**
1. Validate payment amount matches excess fee
2. Generate mock transaction ID
3. Simulate payment processing (95% success rate)
4. Return payment result
5. Update baggage payment status

### Payment Scenarios

**No Excess Fee:**
- Skip payment processing
- Set transaction ID: `NO-PAYMENT-REQUIRED`
- Automatically transition to PAYMENT_COMPLETED

**With Excess Fee:**
- Require payment processing
- Validate amount matches excess fee
- Process payment through mock service
- Transition to PAYMENT_COMPLETED on success

## Error Handling

### Exception Types

| Exception | HTTP Status | Description |
|-----------|-------------|-------------|
| `CheckInNotFoundException` | 404 | Check-in not found |
| `InvalidCheckInStateException` | 400 | Invalid state transition or operation |
| `BaggageNotFoundException` | 404 | Baggage not found for check-in |
| `PaymentFailedException` | 402 | Payment processing failed |
| `SeatConflictException` | 409 | Seat unavailable or concurrent update |

### Example Error Responses

**Invalid State Transition:**
```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Cannot add baggage. Check-in is in COMPLETED state",
  "path": "/api/v1/check-ins/CHK-12345678/baggage",
  "timestamp": "2026-02-27T10:30:00"
}
```

**Payment Amount Mismatch:**
```json
{
  "status": 402,
  "error": "Payment Required",
  "message": "Payment amount mismatch. Expected: 50.00, Provided: 30.00",
  "path": "/api/v1/check-ins/CHK-12345678/payment",
  "timestamp": "2026-02-27T10:31:00"
}
```

**Check-In Not Found:**
```json
{
  "status": 404,
  "error": "Not Found",
  "message": "Check-in not found: CHK-INVALID",
  "path": "/api/v1/check-ins/CHK-INVALID",
  "timestamp": "2026-02-27T10:30:00"
}
```

## Audit Logging

All check-in state transitions are logged asynchronously to the `audit_logs` table:

**Audit Log Entries:**

**Check-In Started:**
```json
{
  "entityType": "CheckIn",
  "entityId": "CHK-12345678",
  "action": "CHECK_IN_STARTED",
  "oldState": null,
  "newState": "{\"status\":\"PENDING\",\"seatId\":1}",
  "userId": "P123456",
  "timestamp": "2026-02-27T10:30:00"
}
```

**Baggage Added:**
```json
{
  "entityType": "CheckIn",
  "entityId": "CHK-12345678",
  "action": "BAGGAGE_ADDED",
  "oldState": "{\"status\":\"PENDING\"}",
  "newState": "{\"status\":\"BAGGAGE_ADDED\",\"baggageId\":1}",
  "userId": "P123456",
  "timestamp": "2026-02-27T10:30:30"
}
```

**Payment Completed:**
```json
{
  "entityType": "CheckIn",
  "entityId": "CHK-12345678",
  "action": "PAYMENT_COMPLETED",
  "oldState": "{\"status\":\"BAGGAGE_ADDED\"}",
  "newState": "{\"status\":\"PAYMENT_COMPLETED\",\"transactionId\":\"TXN-ABC12345\"}",
  "userId": "P123456",
  "timestamp": "2026-02-27T10:31:00"
}
```

**Check-In Completed:**
```json
{
  "entityType": "CheckIn",
  "entityId": "CHK-12345678",
  "action": "CHECK_IN_COMPLETED",
  "oldState": "{\"status\":\"PAYMENT_COMPLETED\"}",
  "newState": "{\"status\":\"COMPLETED\",\"completedAt\":\"2026-02-27T10:32:00\"}",
  "userId": "P123456",
  "timestamp": "2026-02-27T10:32:00"
}
```

**Check-In Cancelled:**
```json
{
  "entityType": "CheckIn",
  "entityId": "CHK-12345678",
  "action": "CHECK_IN_CANCELLED",
  "oldState": "{\"status\":\"PENDING\"}",
  "newState": "{\"status\":\"CANCELLED\",\"cancelledAt\":\"2026-02-27T10:31:30\"}",
  "userId": "P123456",
  "timestamp": "2026-02-27T10:31:30"
}
```

## Testing

### Unit Tests

Location: `src/test/java/com/skyhigh/service/CheckInServiceTest.java`

**Test Coverage:**
- ✅ Start check-in with valid request
- ✅ Start check-in when active check-in exists
- ✅ Add baggage when check-in is pending
- ✅ Add baggage when check-in is not pending
- ✅ Process payment when no excess fee
- ✅ Process payment when excess fee exists
- ✅ Process payment with amount mismatch
- ✅ Process payment when payment fails
- ✅ Confirm check-in when payment completed
- ✅ Confirm check-in when not payment completed
- ✅ Cancel check-in when pending
- ✅ Cancel check-in when already cancelled
- ✅ Cancel check-in when completed
- ✅ Get check-in details when exists
- ✅ Get check-in details when not found

**Running Tests:**
```bash
mvn test -Dtest=CheckInServiceTest
```

**Coverage Report:**
```bash
mvn clean test jacoco:report
```

## Configuration

### Application Properties

```yaml
app:
  baggage:
    max-weight: 25 # kg
    excess-fee-per-kg: 10 # USD per kg
  payment:
    mock-success-rate: 0.95 # 95% success rate for mock payments
```

### Database Indexes

Ensure these indexes exist for optimal performance:

```sql
CREATE INDEX idx_passenger_flight ON check_ins(passenger_id, flight_id);
CREATE INDEX idx_check_in_status ON check_ins(status);
CREATE INDEX idx_check_in_time ON check_ins(check_in_time);
CREATE INDEX idx_baggage_check_in ON baggage(check_in_id);
CREATE INDEX idx_payment_status ON baggage(payment_status);
```

## Integration Points

### With Seat Management Module

**Dependencies:**
- `SeatService.reserveSeat()`: Reserve seat when check-in starts
- `SeatService.confirmSeat()`: Confirm seat when check-in completes
- `SeatService.releaseSeat()`: Release seat when check-in is cancelled

**Seat State Synchronization:**
- Check-in PENDING → Seat HELD
- Check-in COMPLETED → Seat CONFIRMED
- Check-in CANCELLED → Seat AVAILABLE

### With External Services (Mock)

**Weight Service:**
- Validates baggage weight
- Calculates excess weight
- Returns validation result

**Payment Service:**
- Processes payment transactions
- Generates transaction IDs
- Simulates success/failure (95% success rate)

## Performance Considerations

### Transaction Boundaries

- Keep transactions short and focused
- Avoid long-running operations within transactions
- Use async processing for audit logs

### Database Queries

- Use indexed columns for lookups
- Avoid N+1 query problems
- Use appropriate fetch strategies

### Caching

Check-in data is not cached as it changes frequently and requires real-time consistency.

## Error Scenarios and Handling

### 1. Seat Expires During Check-In

**Scenario:** User starts check-in but doesn't complete within 120 seconds

**Handling:**
- Seat expiration scheduler releases seat
- Check-in remains in current state
- User must cancel and restart check-in

### 2. Concurrent Check-In Attempts

**Scenario:** Same passenger tries to check-in twice for same flight

**Handling:**
- Second attempt is rejected
- Error: "Active check-in already exists"
- User must complete or cancel existing check-in

### 3. Payment Processing Failure

**Scenario:** Payment service returns failure status

**Handling:**
- Check-in remains in BAGGAGE_ADDED state
- User can retry payment
- Seat hold timer continues

### 4. Invalid State Transitions

**Scenario:** User tries to skip steps (e.g., confirm without payment)

**Handling:**
- Operation rejected with clear error message
- Check-in remains in current state
- User must follow correct workflow

## Troubleshooting

### Issue: Check-in stuck in PENDING state

**Check:**
1. Verify baggage was added successfully
2. Check for errors in application logs
3. Verify seat is still held (not expired)

**Resolution:**
- If seat expired, cancel and restart check-in
- If baggage failed, retry adding baggage

### Issue: Payment processing fails

**Check:**
1. Verify payment amount matches excess fee
2. Check mock payment service configuration
3. Review payment service logs

**Resolution:**
- Retry payment with correct amount
- Check `app.payment.mock-success-rate` configuration

### Issue: Cannot confirm check-in

**Check:**
1. Verify check-in is in PAYMENT_COMPLETED state
2. Verify seat is still held
3. Check for transaction errors

**Resolution:**
- Complete missing steps (baggage, payment)
- If seat expired, cancel and restart

## Performance Benchmarks

| Operation | Target | Expected (MVP) |
|-----------|--------|----------------|
| Start Check-In | < 500ms | 200-400ms |
| Add Baggage | < 300ms | 100-200ms |
| Process Payment | < 500ms | 150-300ms |
| Confirm Check-In | < 1s | 500-900ms |
| Cancel Check-In | < 500ms | 200-400ms |

## Future Enhancements

### Phase 2+

- **Real Payment Gateway**: Integrate with Stripe/PayPal
- **Real Weight Service**: Integrate with airport baggage systems
- **Email Notifications**: Send confirmation emails
- **SMS Notifications**: Send SMS alerts
- **Boarding Pass Generation**: Generate PDF boarding passes
- **Multi-Baggage Support**: Allow multiple bags per check-in
- **Payment Refunds**: Handle refunds for cancelled check-ins

## References

- [TRD.md Section 9](../TRD.md#9-external-services) - External Services
- [PRD.md Section 6](../PRD.md#6-check-in-flow) - Check-In Flow Requirements
- [PRD.md Section 7](../PRD.md#7-baggage-management) - Baggage Management
- [SEAT_MANAGEMENT.md](./SEAT_MANAGEMENT.md) - Seat Management Module
- [database-patterns.mdc](../.cursor/rules/database-patterns.mdc) - Database Patterns
- [backend-java-standards.mdc](../.cursor/rules/backend-java-standards.mdc) - Backend Standards
