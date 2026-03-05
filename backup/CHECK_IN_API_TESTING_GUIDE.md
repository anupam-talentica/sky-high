# Check-In API Testing Guide

## Overview

This guide provides step-by-step instructions for manually testing the Check-In Module APIs using tools like Postman, curl, or any HTTP client.

## Prerequisites

1. Backend application running on `http://localhost:8080`
2. Database initialized with sample data
3. Valid JWT token (obtain from login endpoint)

## Step 0: Obtain JWT Token

### Login Request

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "john@example.com",
    "password": "demo123"
  }'
```

**Response:**
```json
{
  "token": "eyJhbGciOiJIUzUxMiJ9...",
  "passengerId": "P123456",
  "email": "john@example.com",
  "firstName": "John",
  "lastName": "Doe"
}
```

**Save the token** for use in subsequent requests as: `Authorization: Bearer <token>`

---

## Test Scenario 1: Complete Check-In Without Excess Baggage

### Step 1: Start Check-In

```bash
curl -X POST http://localhost:8080/api/v1/check-ins \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <your-token>" \
  -d '{
    "passengerId": "P123456",
    "flightId": "FL001",
    "seatNumber": "12A"
  }'
```

**Expected Response (201 Created):**
```json
{
  "checkInId": "CHK-12345678",
  "passengerId": "P123456",
  "flightId": "FL001",
  "seatId": 1,
  "seatNumber": "12A",
  "status": "PENDING",
  "checkInTime": "2026-02-27T10:30:00",
  "createdAt": "2026-02-27T10:30:00",
  "updatedAt": "2026-02-27T10:30:00",
  "message": "Check-in started successfully. Please add baggage details."
}
```

**Verify:**
- ✅ Check-in ID is generated
- ✅ Status is PENDING
- ✅ Seat is reserved (check seat map API)

---

### Step 2: Add Baggage (Under 25 kg)

```bash
curl -X POST http://localhost:8080/api/v1/check-ins/CHK-12345678/baggage \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <your-token>" \
  -d '{
    "weightKg": 20.0,
    "dimensions": "55x40x23",
    "baggageType": "CHECKED"
  }'
```

**Expected Response (200 OK):**
```json
{
  "baggageId": 1,
  "checkInId": "CHK-12345678",
  "weightKg": 20.0,
  "dimensions": "55x40x23",
  "baggageType": "CHECKED",
  "excessWeightKg": 0,
  "excessFee": 0,
  "paymentStatus": "PAID",
  "message": "No excess baggage fee"
}
```

**Verify:**
- ✅ Excess fee is 0
- ✅ Payment status is PAID (no payment required)
- ✅ Check-in status updated to BAGGAGE_ADDED

---

### Step 3: Process Payment (No Payment Required)

```bash
curl -X POST http://localhost:8080/api/v1/check-ins/CHK-12345678/payment \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <your-token>" \
  -d '{
    "amount": 0,
    "paymentMethod": "CARD"
  }'
```

**Expected Response (200 OK):**
```json
{
  "transactionId": "NO-PAYMENT-REQUIRED",
  "amount": 0,
  "status": "PAID",
  "message": "No payment required",
  "processedAt": "2026-02-27T10:31:00"
}
```

**Verify:**
- ✅ Transaction ID is NO-PAYMENT-REQUIRED
- ✅ Status is PAID
- ✅ Check-in status updated to PAYMENT_COMPLETED

---

### Step 4: Confirm Check-In

```bash
curl -X POST http://localhost:8080/api/v1/check-ins/CHK-12345678/confirm \
  -H "Authorization: Bearer <your-token>"
```

**Expected Response (200 OK):**
```json
{
  "checkInId": "CHK-12345678",
  "passengerId": "P123456",
  "flightId": "FL001",
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

**Verify:**
- ✅ Status is COMPLETED
- ✅ Completed timestamp is set
- ✅ Seat state is CONFIRMED (check seat map API)

---

## Test Scenario 2: Complete Check-In With Excess Baggage

### Step 1: Start Check-In

Same as Scenario 1, Step 1 (use a different seat number)

---

### Step 2: Add Baggage (Over 25 kg)

```bash
curl -X POST http://localhost:8080/api/v1/check-ins/CHK-87654321/baggage \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <your-token>" \
  -d '{
    "weightKg": 30.0,
    "dimensions": "60x45x25",
    "baggageType": "CHECKED"
  }'
```

**Expected Response (200 OK):**
```json
{
  "baggageId": 2,
  "checkInId": "CHK-87654321",
  "weightKg": 30.0,
  "dimensions": "60x45x25",
  "baggageType": "CHECKED",
  "excessWeightKg": 5.0,
  "excessFee": 50.00,
  "paymentStatus": "PENDING",
  "message": "Excess baggage fee: $50.00"
}
```

**Verify:**
- ✅ Excess weight calculated: 30 - 25 = 5 kg
- ✅ Excess fee calculated: 5 × $10 = $50.00
- ✅ Payment status is PENDING

---

### Step 3: Process Payment (With Excess Fee)

```bash
curl -X POST http://localhost:8080/api/v1/check-ins/CHK-87654321/payment \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <your-token>" \
  -d '{
    "amount": 50.00,
    "paymentMethod": "CARD",
    "cardNumber": "4111111111111111",
    "cardHolderName": "John Doe"
  }'
```

**Expected Response (200 OK):**
```json
{
  "transactionId": "TXN-ABC12345",
  "amount": 50.00,
  "status": "PAID",
  "message": "Payment processed successfully",
  "processedAt": "2026-02-27T10:31:00"
}
```

**Verify:**
- ✅ Transaction ID is generated (TXN-XXXXXXXX format)
- ✅ Status is PAID
- ✅ Check-in status updated to PAYMENT_COMPLETED

---

### Step 4: Confirm Check-In

Same as Scenario 1, Step 4

---

## Test Scenario 3: Cancel Check-In

### Step 1: Start Check-In

Same as Scenario 1, Step 1

---

### Step 2: Cancel Check-In

```bash
curl -X POST http://localhost:8080/api/v1/check-ins/CHK-12345678/cancel \
  -H "Authorization: Bearer <your-token>"
```

**Expected Response (200 OK):**
```json
{
  "checkInId": "CHK-12345678",
  "passengerId": "P123456",
  "flightId": "FL001",
  "seatId": 1,
  "status": "CANCELLED",
  "checkInTime": "2026-02-27T10:30:00",
  "cancelledAt": "2026-02-27T10:31:30",
  "createdAt": "2026-02-27T10:30:00",
  "updatedAt": "2026-02-27T10:31:30",
  "message": "Check-in cancelled successfully. Seat has been released."
}
```

**Verify:**
- ✅ Status is CANCELLED
- ✅ Cancelled timestamp is set
- ✅ Seat is released to AVAILABLE (check seat map API)

---

## Test Scenario 4: Error Cases

### 4.1 Invalid State Transition

**Try to add baggage after check-in is completed:**

```bash
curl -X POST http://localhost:8080/api/v1/check-ins/CHK-COMPLETED/baggage \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <your-token>" \
  -d '{
    "weightKg": 20.0,
    "baggageType": "CHECKED"
  }'
```

**Expected Response (400 Bad Request):**
```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Cannot add baggage. Check-in is in COMPLETED state",
  "path": "/api/v1/check-ins/CHK-COMPLETED/baggage",
  "timestamp": "2026-02-27T10:35:00"
}
```

---

### 4.2 Check-In Not Found

```bash
curl -X GET http://localhost:8080/api/v1/check-ins/CHK-INVALID \
  -H "Authorization: Bearer <your-token>"
```

**Expected Response (404 Not Found):**
```json
{
  "status": 404,
  "error": "Not Found",
  "message": "Check-in not found: CHK-INVALID",
  "path": "/api/v1/check-ins/CHK-INVALID",
  "timestamp": "2026-02-27T10:35:00"
}
```

---

### 4.3 Payment Amount Mismatch

```bash
curl -X POST http://localhost:8080/api/v1/check-ins/CHK-12345678/payment \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <your-token>" \
  -d '{
    "amount": 30.00,
    "paymentMethod": "CARD"
  }'
```

**Expected Response (402 Payment Required):**
```json
{
  "status": 402,
  "error": "Payment Required",
  "message": "Payment amount mismatch. Expected: 50.00, Provided: 30.00",
  "path": "/api/v1/check-ins/CHK-12345678/payment",
  "timestamp": "2026-02-27T10:35:00"
}
```

---

### 4.4 Active Check-In Already Exists

**Try to start second check-in for same passenger and flight:**

```bash
curl -X POST http://localhost:8080/api/v1/check-ins \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <your-token>" \
  -d '{
    "passengerId": "P123456",
    "flightId": "FL001",
    "seatNumber": "13B"
  }'
```

**Expected Response (400 Bad Request):**
```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Active check-in already exists for this passenger and flight",
  "path": "/api/v1/check-ins",
  "timestamp": "2026-02-27T10:35:00"
}
```

---

### 4.5 Validation Errors

**Missing required fields:**

```bash
curl -X POST http://localhost:8080/api/v1/check-ins \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <your-token>" \
  -d '{
    "passengerId": "P123456"
  }'
```

**Expected Response (400 Bad Request):**
```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Flight ID is required, Seat number is required",
  "path": "/api/v1/check-ins",
  "timestamp": "2026-02-27T10:35:00"
}
```

---

## Test Scenario 5: Get Check-In Details

```bash
curl -X GET http://localhost:8080/api/v1/check-ins/CHK-12345678 \
  -H "Authorization: Bearer <your-token>"
```

**Expected Response (200 OK):**
```json
{
  "checkInId": "CHK-12345678",
  "passengerId": "P123456",
  "flightId": "FL001",
  "seatId": 1,
  "status": "PENDING",
  "checkInTime": "2026-02-27T10:30:00",
  "createdAt": "2026-02-27T10:30:00",
  "updatedAt": "2026-02-27T10:30:00"
}
```

---

## Postman Collection

### Environment Variables

Create a Postman environment with:
- `base_url`: `http://localhost:8080`
- `token`: `<your-jwt-token>`

### Collection Structure

```
SkyHigh Check-In API
├── Auth
│   └── Login
├── Check-In
│   ├── Start Check-In
│   ├── Add Baggage (No Excess)
│   ├── Add Baggage (With Excess)
│   ├── Process Payment (No Fee)
│   ├── Process Payment (With Fee)
│   ├── Confirm Check-In
│   ├── Cancel Check-In
│   └── Get Check-In Details
└── Error Cases
    ├── Invalid State Transition
    ├── Check-In Not Found
    ├── Payment Amount Mismatch
    └── Active Check-In Exists
```

---

## Testing Checklist

### Happy Path Tests
- [ ] Start check-in with valid data
- [ ] Add baggage under 25 kg (no excess fee)
- [ ] Add baggage over 25 kg (with excess fee)
- [ ] Process payment with correct amount
- [ ] Process payment when no payment required
- [ ] Confirm check-in after payment
- [ ] Get check-in details at each stage
- [ ] Cancel check-in before completion

### Error Case Tests
- [ ] Start check-in with missing fields
- [ ] Start check-in when active check-in exists
- [ ] Add baggage when not in PENDING state
- [ ] Process payment with wrong amount
- [ ] Confirm check-in before payment
- [ ] Cancel already cancelled check-in
- [ ] Cancel completed check-in
- [ ] Get non-existent check-in

### Integration Tests
- [ ] Verify seat state changes (AVAILABLE → HELD → CONFIRMED)
- [ ] Verify seat released when check-in cancelled
- [ ] Verify audit logs created for each state change
- [ ] Verify transaction rollback on errors

### Performance Tests
- [ ] Start check-in completes in < 500ms
- [ ] Add baggage completes in < 300ms
- [ ] Process payment completes in < 500ms
- [ ] Confirm check-in completes in < 1s

---

## Common Issues and Solutions

### Issue: 401 Unauthorized

**Cause:** JWT token is missing, invalid, or expired

**Solution:**
1. Obtain new token from login endpoint
2. Ensure token is included in Authorization header
3. Check token format: `Bearer <token>`

---

### Issue: 409 Conflict (Seat Unavailable)

**Cause:** Seat is already held or confirmed by another passenger

**Solution:**
1. Choose a different seat
2. Check seat map for available seats
3. Wait for seat to be released (if held)

---

### Issue: 400 Invalid State Transition

**Cause:** Trying to perform operation in wrong state

**Solution:**
1. Check current check-in status
2. Follow correct workflow: PENDING → BAGGAGE_ADDED → PAYMENT_COMPLETED → COMPLETED
3. Complete missing steps before proceeding

---

### Issue: 402 Payment Amount Mismatch

**Cause:** Payment amount doesn't match excess baggage fee

**Solution:**
1. Get baggage details to see exact excess fee
2. Provide exact amount in payment request
3. Ensure amount has correct decimal places

---

## Monitoring and Debugging

### Check Application Logs

```bash
tail -f logs/skyhigh-core.log
```

**Look for:**
- Check-in started/completed messages
- Payment processing logs
- State transition logs
- Error messages

### Check Audit Logs

Query the database:

```sql
SELECT * FROM audit_logs 
WHERE entity_type = 'CheckIn' 
ORDER BY timestamp DESC 
LIMIT 10;
```

### Check Seat State

Query the database:

```sql
SELECT seat_id, seat_number, state, held_by, confirmed_by 
FROM seats 
WHERE flight_id = 'FL001' 
AND seat_number = '12A';
```

---

## Performance Benchmarks

| Operation | Target | Expected |
|-----------|--------|----------|
| Start Check-In | < 500ms | 200-400ms |
| Add Baggage | < 300ms | 100-200ms |
| Process Payment | < 500ms | 150-300ms |
| Confirm Check-In | < 1s | 500-900ms |
| Cancel Check-In | < 500ms | 200-400ms |
| Get Check-In Details | < 200ms | 50-150ms |

---

## Sample Test Data

### Hardcoded Passengers

| Passenger ID | Email | Password |
|--------------|-------|----------|
| P123456 | john@example.com | demo123 |
| P789012 | jane@example.com | demo456 |

### Sample Flight

| Flight ID | Flight Number | Route | Total Seats |
|-----------|---------------|-------|-------------|
| FL001 | SK1234 | JFK → LAX | 189 |

### Available Seats

Check seat map API to see all available seats:

```bash
curl -X GET http://localhost:8080/api/v1/flights/FL001/seat-map \
  -H "Authorization: Bearer <your-token>"
```

---

## Automated Testing Script

### Bash Script

```bash
#!/bin/bash

BASE_URL="http://localhost:8080"
EMAIL="john@example.com"
PASSWORD="demo123"

# 1. Login
echo "=== Step 1: Login ==="
TOKEN=$(curl -s -X POST $BASE_URL/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}" \
  | jq -r '.token')

echo "Token: $TOKEN"

# 2. Start Check-In
echo -e "\n=== Step 2: Start Check-In ==="
CHECK_IN=$(curl -s -X POST $BASE_URL/api/v1/check-ins \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "passengerId": "P123456",
    "flightId": "FL001",
    "seatNumber": "12A"
  }')

CHECK_IN_ID=$(echo $CHECK_IN | jq -r '.checkInId')
echo "Check-In ID: $CHECK_IN_ID"
echo $CHECK_IN | jq

# 3. Add Baggage
echo -e "\n=== Step 3: Add Baggage ==="
BAGGAGE=$(curl -s -X POST $BASE_URL/api/v1/check-ins/$CHECK_IN_ID/baggage \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "weightKg": 30.0,
    "dimensions": "60x45x25",
    "baggageType": "CHECKED"
  }')

echo $BAGGAGE | jq

# 4. Process Payment
echo -e "\n=== Step 4: Process Payment ==="
PAYMENT=$(curl -s -X POST $BASE_URL/api/v1/check-ins/$CHECK_IN_ID/payment \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "amount": 50.00,
    "paymentMethod": "CARD"
  }')

echo $PAYMENT | jq

# 5. Confirm Check-In
echo -e "\n=== Step 5: Confirm Check-In ==="
CONFIRM=$(curl -s -X POST $BASE_URL/api/v1/check-ins/$CHECK_IN_ID/confirm \
  -H "Authorization: Bearer $TOKEN")

echo $CONFIRM | jq

echo -e "\n=== Check-In Workflow Completed Successfully! ==="
```

**Save as:** `test-checkin-workflow.sh`

**Run:**
```bash
chmod +x test-checkin-workflow.sh
./test-checkin-workflow.sh
```

---

## References

- [CHECK_IN_MANAGEMENT.md](./CHECK_IN_MANAGEMENT.md) - Module Documentation
- [TRD.md Section 14.1](../TRD.md#141-api-endpoint-summary) - API Endpoint Summary
- [PRD.md Section 6](../PRD.md#6-check-in-flow) - Check-In Flow Requirements
