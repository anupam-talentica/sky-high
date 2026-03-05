# Product Requirements Document (PRD)
# SkyHigh Core – Digital Check-In System (MVP)

**Version:** 1.0 (MVP)  
**Date:** February 27, 2026  
**Status:** Draft  
**Owner:** SkyHigh Airlines Engineering Team

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [Problem Statement](#2-problem-statement)
3. [Goals & Success Metrics](#3-goals--success-metrics)
4. [Functional Requirements](#4-functional-requirements)
5. [Non-Functional Requirements](#5-non-functional-requirements)
6. [System Architecture](#6-system-architecture)
7. [API Specifications](#7-api-specifications)
8. [Data Models](#8-data-models)
9. [Technology Stack](#9-technology-stack)
10. [Out of Scope for MVP](#10-out-of-scope-for-mvp)

---

## 1. Executive Summary

SkyHigh Core is a backend service designed to handle airport self-check-in during peak traffic periods. The MVP focuses on delivering core functionality with a simple, reliable architecture that can be deployed on a single EC2 instance.

### Key Capabilities (MVP)
- **Conflict-free seat assignment** with guaranteed consistency
- **Time-bound seat reservations** (120-second hold mechanism)
- **Automated waitlist management** with seat allocation
- **Baggage validation and payment integration**
- **High-performance seat map access** (P95 < 1 second)
- **Basic abuse detection** with rate limiting

---

## 2. Problem Statement

During popular flight check-in windows, the system must handle:

1. **Race conditions** - Multiple passengers attempting to reserve the same seat simultaneously
2. **Time-bound holds** - Seats reserved for exactly 120 seconds, then automatically released
3. **Performance requirements** - Seat map access must be fast (P95 < 1 second) under load
4. **Concurrent usage** - Hundreds of passengers checking in at the same time
5. **Abuse prevention** - Detect and block rapid seat map scraping

**Impact**: Without proper handling, this leads to double bookings, poor user experience, and system instability.

---

## 3. Goals & Success Metrics

### 3.1 Primary Goals
1. Zero seat assignment conflicts (100% consistency guarantee)
2. Automated 120-second seat hold with auto-release
3. P95 response time < 1 second for seat map access
4. Support 500+ concurrent users per flight
5. Automated waitlist assignment

### 3.2 Success Metrics

| Metric | Target | Measurement Method |
|--------|--------|-------------------|
| Seat conflict rate | 0% | Database audit logs |
| Seat map P95 latency | < 1 second | Application monitoring |
| Concurrent users supported | 500+ | Load testing |
| Abandoned seat holds | 100% auto-released | System logs |
| System uptime | 99% | Infrastructure monitoring |

---

## 4. Functional Requirements

### Format Guide
Each requirement follows this structure:
- **Requirement ID**: Unique identifier
- **Description**: Brief title of the requirement
- **User Story**: As a [user type], I want to [action] so that [benefit]
- **Expected Behavior/Outcome**: Detailed description of system behavior

---

### FR-001 | Seat Availability & Lifecycle Management

**Priority**: P0 (Critical)

#### User Story
As a passenger, I want to see real-time seat availability so that I can select an available seat for my flight.

As a system administrator, I want seats to follow a strict state machine so that seat assignments are always consistent and conflict-free.

#### Expected Behavior/Outcome

**Seat State Machine**:
```
AVAILABLE → HELD → CONFIRMED → CANCELLED
     ↑                            ↓
     └────────────────────────────┘
```

**State Definitions**:

| State | Description | Allowed Transitions |
|-------|-------------|---------------------|
| `AVAILABLE` | Seat is free and can be selected | → HELD |
| `HELD` | Seat temporarily reserved (120s) | → CONFIRMED, → AVAILABLE (timeout) |
| `CONFIRMED` | Seat permanently assigned | → CANCELLED |
| `CANCELLED` | Seat released by passenger | → AVAILABLE |

**System Behavior**:
1. A seat can only transition to `HELD` if current state is `AVAILABLE`
2. A seat in `HELD` state is exclusive to exactly one passenger
3. Only `CONFIRMED` seats can be `CANCELLED` by passengers
4. State transitions must be atomic and logged for audit purposes
5. System enforces state machine rules at database level
6. Concurrent state transition attempts result in only one success
7. All state changes are logged with timestamp and user ID
8. Invalid transitions return HTTP 409 Conflict

---

### FR-002 | Time-Bound Seat Hold (120 Seconds)

**Priority**: P0 (Critical)

#### User Story
As a passenger, I want my selected seat to be held for 120 seconds while I complete my check-in so that no one else can take it, but I also want the seat to be released automatically if I don't complete the process so other passengers can use it.

#### Expected Behavior/Outcome

When a passenger selects a seat, the system must:

1. **Reserve the seat** by transitioning state from `AVAILABLE` to `HELD`
2. **Start a 120-second countdown timer** visible to the passenger
3. **Block other passengers** from reserving the same seat during this period
4. **Automatically release** the seat if check-in is not completed within 120 seconds

**Technical Behavior**:
- Timer is server-side and not dependent on client-side logic
- System handles timer expiration even during server restarts
- Timer precision: ±2 seconds acceptable variance
- Expired holds are cleaned up within 5 seconds of expiration
- Spring `@Scheduled` task runs every 5 seconds to check for expired holds
- Seats where `held_until < NOW()` and `state = 'HELD'` transition to `AVAILABLE`
- System handles 1000+ concurrent timers without performance degradation

---

### FR-003 | Conflict-Free Seat Assignment

**Priority**: P0 (Critical)

#### User Story
As a passenger, I want to be guaranteed that when I successfully reserve a seat, no other passenger can reserve the same seat, even if we both clicked at the exact same time.

#### Expected Behavior/Outcome

**Hard Guarantee**: If multiple passengers attempt to reserve the same seat simultaneously, exactly one reservation must succeed.

**System Behavior**:
1. Uses database-level optimistic locking with `version` column
2. Implements idempotency for seat reservation requests
3. Returns appropriate HTTP status codes:
   - `200 OK`: Reservation successful
   - `409 Conflict`: Seat already reserved by another passenger
   - `423 Locked`: Seat temporarily unavailable (being processed)
4. Provides ACID compliance for all seat assignment transactions
5. Ensures no eventual consistency for seat state (immediate consistency)
6. Guarantees read-after-write consistency for seat availability queries
7. Load test with 500 concurrent users shows zero conflicts
8. Database constraints prevent duplicate seat assignments
9. Failed reservation attempts receive clear error messages

---

### FR-004 | Passenger Cancellation

**Priority**: P1 (High)

#### User Story
As a passenger, I want to be able to cancel my confirmed check-in before my flight departs so that I can change my plans and free up my seat for other passengers.

#### Expected Behavior/Outcome

**System Behavior**:
1. Only `CONFIRMED` seats can be cancelled
2. Cancelled seats immediately transition to `AVAILABLE` (within 1 second)
3. If waitlist exists for that seat, seat is automatically assigned to next eligible passenger
4. Waitlisted passengers are notified within 5 seconds of seat becoming available
5. API endpoint: `POST /api/v1/check-ins/{checkInId}/cancel`
6. Operation is idempotent (can be called multiple times safely)
7. Returns cancellation confirmation with timestamp
8. Audit log records the cancellation with passenger ID and timestamp

---

### FR-005 | Waitlist Management

**Priority**: P1 (High)

#### User Story
As a passenger, I want to join a waitlist when my preferred seat is unavailable so that I can automatically get that seat if it becomes available, without having to constantly check.

#### Expected Behavior/Outcome

**System Behavior**:
1. Passengers can join waitlist for specific seats when unavailable
2. Waitlist follows FIFO (First-In-First-Out) order
3. When seat becomes `AVAILABLE`, system automatically:
   - Assigns seat to next waitlisted passenger
   - Transitions seat to `HELD` for that passenger
   - Sends email notification to passenger
   - Starts 120-second hold timer
4. Waitlist assignment happens within 2 seconds of seat availability
5. Notifications are sent successfully via email (mock/stub service for MVP)
6. Email includes seat number and 120-second countdown
7. If notified passenger doesn't respond within 120s, system moves to next in waitlist
8. If all waitlisted passengers are notified and don't respond, seat becomes publicly available
9. System handles 50+ passengers on waitlist per flight
10. Passengers can leave waitlist at any time via `DELETE /waitlist/{waitlistId}`

---

### FR-006 | Baggage Validation & Payment Integration

**Priority**: P1 (High)

#### User Story
As a passenger, I want to add my baggage information during check-in and be notified if I need to pay for excess weight so that I can complete my check-in with proper baggage handling.

#### Expected Behavior/Outcome

**Business Rules**:
1. Maximum allowed weight: 25kg per bag
2. Overweight threshold: Any bag > 25kg
3. Excess baggage fee: $10 per kg over limit

**Check-In Flow with Baggage**:
1. Passenger selects seat (seat moves to `HELD`)
2. Passenger adds baggage details (weight, type)
3. System calls **Weight Service** (mock/stub) to validate baggage
4. If weight ≤ 25kg:
   - Continue to check-in confirmation
   - No additional payment required
5. If weight > 25kg:
   - **Pause check-in** (seat remains `HELD`, timer continues)
   - Calculate excess fee: (weight - 25kg) × $10
   - Display payment prompt with fee breakdown
   - Call **Payment Service** (mock/stub) to process fee
   - If payment succeeds: Resume check-in, mark status as `IN_PROGRESS`
   - If payment fails: Return to baggage selection, allow retry
   - If timer expires during payment: Release seat, cancel check-in

**Check-In Status States**:

| Status | Description |
|--------|-------------|
| `IN_PROGRESS` | Passenger actively completing check-in |
| `AWAITING_PAYMENT` | Paused for baggage fee payment |
| `COMPLETED` | Check-in successfully confirmed |
| `CANCELLED` | Check-in cancelled or timed out |

**Integration Requirements** (Mock/Stub for MVP):
- **Weight Service**: `POST /api/v1/baggage/validate`
  - Input: `{ baggageWeight: number, unit: "kg" }`
  - Output: `{ isValid: boolean, excessWeight: number, fee: number }`
- **Payment Service**: `POST /api/v1/payments/process`
  - Input: `{ amount: number, currency: "USD", passengerId: string }`
  - Output: `{ transactionId: string, status: "success" | "failed" }`

**System Behavior**:
- Baggage over 25kg pauses check-in and prompts payment
- Seat hold timer continues during payment process
- Payment failure allows passenger to retry or modify baggage
- Check-in status accurately reflects current state
- All baggage and payment transactions are logged for audit

---

### FR-007 | High-Performance Seat Map Access

**Priority**: P0 (Critical)

#### User Story
As a passenger, I want to quickly view the seat map for my flight so that I can see available seats and make my selection without waiting, even when hundreds of other passengers are checking in at the same time.

#### Expected Behavior/Outcome

**Performance Requirements**:

| Metric | Target |
|--------|--------|
| P50 latency | < 300ms |
| P95 latency | < 1 second |
| P99 latency | < 2 seconds |
| Concurrent users | 500+ per flight |

**System Behavior**:
1. Returns seat map with real-time availability for a given flight
2. Includes seat metadata for each seat:
   - Seat number (e.g., "12A")
   - Seat type (window, aisle, middle)
   - Current state (AVAILABLE, HELD, CONFIRMED)
   - Price (if premium seat)
3. Uses in-memory caching (Caffeine) for MVP:
   - Seat map structure cached for 5 minutes
   - Seat availability cached for 10 seconds
   - Cache invalidated immediately on seat state change
   - Fallback to database on cache miss
4. Database indexing on `flightId` and `state` for fast queries
5. Connection pooling for database queries
6. Pagination support for large aircraft (300+ seats)
7. P95 latency < 1 second under 500 concurrent users
8. Seat availability is accurate within 10 seconds
9. Caching reduces database load by 70%+

**API Specification**:
```
GET /api/v1/flights/{flightId}/seat-map

Response: {
  flightId: string,
  aircraft: string,
  totalSeats: number,
  availableSeats: number,
  seats: [
    {
      seatNumber: string,
      seatType: string,
      state: string,
      price: number
    }
  ]
}
```

---

### FR-008 | Abuse & Bot Detection

**Priority**: P1 (High)

#### User Story
As a system administrator, I want to detect and block abusive access patterns (like bots scraping seat maps) so that legitimate passengers have a good experience and system resources are protected.

#### Expected Behavior/Outcome

**Detection Scenario: Rapid Seat Map Access**
- **Trigger**: Single source (IP/user) accessing 50+ different seat maps within 2 seconds
- **Action**: 
  - Block further seat map requests for 5 minutes
  - Return HTTP 429 Too Many Requests
  - Log event with source IP, timestamp, and request count

**Rate Limiting Rules** (MVP):

| Endpoint | Limit | Window | Action on Exceed |
|----------|-------|--------|------------------|
| `GET /seat-map` | 20 requests | 10 seconds | 429 + 1 min cooldown |
| `POST /reserve-seat` | 5 requests | 1 minute | 429 + 5 min cooldown |
| `POST /check-in` | 3 requests | 5 minutes | 429 + 10 min cooldown |

**System Behavior**:
1. Uses in-memory rate limiting (Spring Boot + Bucket4j) for MVP
2. Tracks requests by IP address
3. Stores rate limit counters in application memory
4. System detects 50 seat map accesses in 2 seconds and blocks source
5. Rate limits are enforced consistently across all endpoints
6. Blocked users receive clear error message with retry-after time
7. All rate limit violations are logged with IP, timestamp, and endpoint
8. Future enhancement: Redis for distributed rate limiting across multiple instances

---

### FR-009 | Real-Time Flight Status Integration

**Priority**: P1 (High)

#### User Story
As a passenger, I want to see my flight's current status (on-time, delayed, cancelled) and gate information during check-in so that I have the most up-to-date information before completing my check-in.

As a system administrator, I want to prevent check-ins for cancelled or significantly delayed flights so that passengers are not assigned seats on flights that won't operate as scheduled.

#### Expected Behavior/Outcome

**Flight Status Display**:
1. During check-in initiation, system fetches real-time flight status from AviationStack API
2. Displays flight information:
   - Flight status: Scheduled, Active, Landed, Cancelled, Diverted, Delayed
   - Departure gate and terminal
   - Boarding time
   - Actual departure time (if different from scheduled)
   - Delay duration (if applicable)
3. Updates flight status every 5 minutes during check-in process
4. Caches flight data for 5 minutes to reduce API calls

**Check-In Validation Rules**:
1. **Cancelled Flights**: Block check-in, display message: "This flight has been cancelled. Please contact customer service."
2. **Significantly Delayed Flights** (>3 hours): Display warning: "Your flight is delayed by X hours. Gate information may change."
3. **Gate Changes**: Highlight gate changes prominently: "⚠️ Gate changed from B12 to C5"
4. **On-Time Flights**: Display confirmation: "✓ Flight is on schedule"

**Integration Details**:
- **API Provider**: AviationStack (https://aviationstack.com/)
- **Endpoint**: `GET /flights?flight_iata={flightNumber}&access_key={API_KEY}`
- **Free Tier**: 100 API calls/month (sufficient for MVP demo)
- **Paid Tier**: 500 calls/month at $9.99 (if needed for production)

**Response Data Used**:
```json
{
  "flight_status": "scheduled",
  "departure": {
    "airport": "JFK",
    "timezone": "America/New_York",
    "iata": "JFK",
    "terminal": "4",
    "gate": "B12",
    "scheduled": "2026-02-27T14:30:00+00:00"
  },
  "arrival": {
    "airport": "LAX",
    "timezone": "America/Los_Angeles",
    "iata": "LAX",
    "terminal": "5",
    "gate": "52A",
    "scheduled": "2026-02-27T17:45:00+00:00"
  },
  "airline": {
    "name": "SkyHigh Airlines",
    "iata": "SK"
  },
  "flight": {
    "number": "1234",
    "iata": "SK1234"
  }
}
```

**Error Handling**:
1. If AviationStack API is unavailable:
   - Fallback to database flight information
   - Display warning: "Unable to fetch real-time status"
   - Allow check-in to proceed with cached data
2. If flight not found in AviationStack:
   - Use local database as source of truth
   - Log discrepancy for investigation
3. Circuit breaker pattern: After 3 consecutive failures, stop calling API for 5 minutes

**Caching Strategy**:
- Cache flight status for 5 minutes
- Cache key: `flight:status:{flightNumber}:{date}`
- Invalidate on manual refresh by passenger
- Reduce API calls while maintaining freshness

**System Behavior**:
1. Flight status is fetched when passenger initiates check-in
2. Status is displayed prominently at top of check-in flow
3. Gate and terminal information is included in boarding pass
4. Delayed/cancelled flights trigger appropriate workflows
5. All API calls are logged with response time and status
6. Failed API calls trigger fallback to local database
7. System continues to function if AviationStack is unavailable

**Acceptance Criteria**:
- ✅ Real-time flight status displayed within 2 seconds of check-in start
- ✅ Cancelled flights block check-in with clear message
- ✅ Gate changes are highlighted prominently
- ✅ System falls back gracefully if API is unavailable
- ✅ API response time < 1 second (P95)
- ✅ Flight status cache reduces API calls by 80%+

---

## 5. Non-Functional Requirements

### 5.1 Performance

**NFR-001: Response Time**
- P50 latency: < 300ms for all API endpoints
- P95 latency: < 1 second for seat map access
- P99 latency: < 2 seconds for check-in completion

**NFR-002: Throughput**
- Support 500+ concurrent users per flight
- Process 5,000+ check-ins per hour during peak times

**NFR-003: Resource Utilization**
- CPU utilization: < 70% under normal load
- Memory utilization: < 80% under normal load

### 5.2 Availability & Reliability

**NFR-004: Uptime**
- Target: 99% uptime
- Planned maintenance windows: < 4 hours per month

**NFR-005: Data Integrity**
- Zero data loss for confirmed check-ins
- Automated database backups every 6 hours
- Backup retention: 30 days

**NFR-006: Fault Tolerance**
- Graceful degradation when external services (Weight, Payment) fail
- Retry logic with exponential backoff (max 3 retries)

### 5.3 Security

**NFR-007: Authentication & Authorization**
- Simple JWT-based authentication
- API key authentication for external services
- Basic role-based access control (passenger, admin)

**NFR-008: Data Encryption**
- TLS 1.3 for data in transit
- Encrypted database backups

**NFR-009: Audit Logging**
- Log all seat state transitions with user ID and timestamp
- Retain audit logs for 90 days

### 5.4 Observability

**NFR-010: Logging**
- Structured logging (JSON format)
- Log levels: INFO, WARN, ERROR
- Centralized logging (CloudWatch)

**NFR-011: Monitoring**
- Basic metrics dashboard
- Key metrics:
  - Request rate, error rate, latency
  - CPU, memory, disk usage
  - Database connection pool utilization
- Alerting on critical errors

### 5.5 Maintainability

**NFR-012: Code Quality**
- Test coverage: > 80%
- Code review required for all changes
- Automated linting and formatting

**NFR-013: Deployment**
- CI/CD pipeline with automated testing
- Automated rollback on failure
- Infrastructure as Code (Terraform)

---

## 6. System Architecture

### 6.1 High-Level Architecture (MVP)

```
┌─────────────────────────────────────────────────────────┐
│                    Internet Gateway                      │
└────────────────────────┬────────────────────────────────┘
                         │
                         ▼
              ┌──────────────────────┐
              │   Application Load   │
              │   Balancer (ALB)     │
              └──────────┬───────────┘
                         │
                         ▼
              ┌──────────────────────┐
              │   EC2 Instance       │
              │  (Spring Boot App)   │
              │                      │
              │  - REST API          │
              │  - Business Logic    │
              │  - In-Memory Cache   │
              │  - Rate Limiting     │
              │  - Scheduled Tasks   │
              └──────────┬───────────┘
                         │
                         ▼
              ┌──────────────────────┐
              │   Amazon RDS         │
              │   (PostgreSQL)       │
              │                      │
              │  - Primary Database  │
              │  - Automated Backups │
              └──────────────────────┘

┌─────────────────────────────────────────────────────────┐
│                    Frontend (Separate)                   │
│                                                          │
│   S3 Bucket (Static Hosting)                            │
│   + CloudFront (CDN)                                    │
│                                                          │
│   - React Application                                   │
│   - Calls Backend API                                   │
└─────────────────────────────────────────────────────────┘

External Services (Mock/Stub):
┌──────────┐    ┌──────────┐    ┌──────────┐
│  Weight  │    │ Payment  │    │  Email   │
│ Service  │    │ Service  │    │ Service  │
└──────────┘    └──────────┘    └──────────┘
```

### 6.2 Component Descriptions

**Application Load Balancer (ALB)**:
- Distributes traffic (future-proofing for multiple instances)
- Health checks every 30 seconds
- SSL/TLS termination

**EC2 Instance (Spring Boot Application)**:
- Instance Type: t3.medium (2 vCPU, 4 GB RAM)
- Handles all business logic
- In-memory caching (Caffeine)
- Scheduled tasks for seat hold expiration
- Rate limiting (Bucket4j)

**Amazon RDS (PostgreSQL)**:
- Instance Type: db.t3.micro (1 vCPU, 1 GB RAM)
- Storage: 20 GB SSD
- Automated backups enabled
- Multi-AZ deployment for reliability

**S3 + CloudFront (Frontend)**:
- Static website hosting on S3
- CloudFront for global CDN
- React application served to users

**External Services (Mock/Stub)**:
- Weight Service: Validates baggage weight
- Payment Service: Processes baggage fees
- Email Service: Sends notifications

### 6.3 Data Flow

**Seat Reservation Flow**:
1. User requests seat map from frontend
2. Frontend calls `GET /api/v1/flights/{flightId}/seat-map`
3. Backend checks in-memory cache
4. If cache miss, query RDS PostgreSQL
5. Return seat map to frontend
6. User selects seat
7. Frontend calls `POST /api/v1/flights/{flightId}/seats/{seatNumber}/reserve`
8. Backend uses optimistic locking to reserve seat
9. Start 120-second hold timer (tracked in database)
10. Return reservation confirmation

**Timer Expiration Flow**:
1. Scheduled task runs every 5 seconds
2. Query database for expired holds: `held_until < NOW() AND state = 'HELD'`
3. Transition expired seats to `AVAILABLE`
4. Invalidate cache for affected flights
5. If waitlist exists, assign to next passenger

---

## 7. API Specifications

### 7.1 Base URL
```
Production: https://api.skyhigh.com/v1
```

### 7.2 Authentication
All API requests require JWT token in the `Authorization` header:
```
Authorization: Bearer <jwt_token>
```

### 7.3 Core Endpoints

#### 7.3.1 Get Seat Map
```
GET /flights/{flightId}/seat-map

Response 200:
{
  "flightId": "SK1234",
  "aircraft": "Boeing 737-800",
  "totalSeats": 189,
  "availableSeats": 45,
  "seats": [
    {
      "seatNumber": "12A",
      "seatType": "window",
      "state": "AVAILABLE",
      "price": 0
    }
  ]
}

Error Responses:
  404: Flight not found
  429: Rate limit exceeded
  500: Internal server error
```

#### 7.3.2 Reserve Seat
```
POST /flights/{flightId}/seats/{seatNumber}/reserve

Request Body:
{
  "passengerId": "P123456"
}

Response 200:
{
  "reservationId": "R789012",
  "seatNumber": "12A",
  "state": "HELD",
  "expiresAt": "2026-02-27T10:32:00Z",
  "remainingSeconds": 120
}

Error Responses:
  409: Seat already reserved
  404: Seat or flight not found
  423: Seat temporarily locked
  429: Rate limit exceeded
```

#### 7.3.3 Add Baggage
```
POST /check-ins/{checkInId}/baggage

Request Body:
{
  "bags": [
    {
      "weight": 20,
      "unit": "kg",
      "type": "checked"
    }
  ]
}

Response 200 (within limit):
{
  "checkInId": "C123456",
  "status": "IN_PROGRESS",
  "totalWeight": 20,
  "totalFee": 0
}

Response 200 (overweight):
{
  "checkInId": "C123456",
  "status": "AWAITING_PAYMENT",
  "totalWeight": 30,
  "excessWeight": 5,
  "totalFee": 50,
  "paymentRequired": true
}
```

#### 7.3.4 Process Payment
```
POST /check-ins/{checkInId}/payment

Request Body:
{
  "amount": 50,
  "currency": "USD",
  "paymentMethod": "credit_card",
  "paymentToken": "tok_visa_4242"
}

Response 200:
{
  "transactionId": "TXN789012",
  "status": "success",
  "amount": 50,
  "currency": "USD",
  "checkInStatus": "IN_PROGRESS"
}
```

#### 7.3.5 Confirm Check-In
```
POST /check-ins/{checkInId}/confirm

Response 200:
{
  "checkInId": "C123456",
  "status": "COMPLETED",
  "seatNumber": "12A",
  "boardingPass": {
    "barcode": "SK1234P12345612A",
    "gate": "B12",
    "boardingTime": "2026-02-27T14:30:00Z"
  }
}
```

#### 7.3.6 Cancel Check-In
```
POST /check-ins/{checkInId}/cancel

Response 200:
{
  "checkInId": "C123456",
  "status": "CANCELLED",
  "seatNumber": "12A",
  "seatState": "AVAILABLE",
  "cancelledAt": "2026-02-27T10:35:00Z"
}
```

#### 7.3.7 Join Waitlist
```
POST /flights/{flightId}/seats/{seatNumber}/waitlist

Request Body:
{
  "passengerId": "P123456"
}

Response 200:
{
  "waitlistId": "W123456",
  "position": 3,
  "estimatedWaitTime": "15-30 minutes"
}
```

#### 7.3.8 Leave Waitlist
```
DELETE /waitlist/{waitlistId}

Response 204: No Content
```

#### 7.3.9 Get Flight Status
```
GET /flights/{flightId}/status

Response 200:
{
  "flightId": "SK1234",
  "flightNumber": "SK1234",
  "status": "scheduled",
  "departure": {
    "airport": "JFK",
    "iata": "JFK",
    "terminal": "4",
    "gate": "B12",
    "scheduledTime": "2026-02-27T14:30:00Z",
    "actualTime": "2026-02-27T14:30:00Z"
  },
  "arrival": {
    "airport": "LAX",
    "iata": "LAX",
    "terminal": "5",
    "gate": "52A",
    "scheduledTime": "2026-02-27T17:45:00Z",
    "estimatedTime": "2026-02-27T17:45:00Z"
  },
  "delay": {
    "duration": 0,
    "reason": null
  },
  "lastUpdated": "2026-02-27T10:30:00Z"
}

Response 200 (Delayed):
{
  "flightId": "SK1234",
  "flightNumber": "SK1234",
  "status": "delayed",
  "departure": {
    "airport": "JFK",
    "iata": "JFK",
    "terminal": "4",
    "gate": "B12",
    "scheduledTime": "2026-02-27T14:30:00Z",
    "actualTime": "2026-02-27T16:00:00Z"
  },
  "arrival": {
    "airport": "LAX",
    "iata": "LAX",
    "terminal": "5",
    "gate": "52A",
    "scheduledTime": "2026-02-27T17:45:00Z",
    "estimatedTime": "2026-02-27T19:15:00Z"
  },
  "delay": {
    "duration": 90,
    "reason": "Weather conditions"
  },
  "lastUpdated": "2026-02-27T10:30:00Z"
}

Error Responses:
  404: Flight not found
  503: Flight status service unavailable (fallback to local data)
```

### 7.4 Error Response Format
```json
{
  "error": {
    "code": "SEAT_ALREADY_RESERVED",
    "message": "The selected seat is no longer available",
    "details": {
      "seatNumber": "12A",
      "currentState": "HELD"
    },
    "timestamp": "2026-02-27T10:30:00Z",
    "requestId": "req_abc123"
  }
}
```

---

## 8. Data Models

### 8.1 Entity Relationship Diagram

```
┌─────────────┐         ┌─────────────┐         ┌─────────────┐
│   Flight    │         │    Seat     │         │  Passenger  │
├─────────────┤         ├─────────────┤         ├─────────────┤
│ flightId PK │────────<│ seatId   PK │         │passengerId PK│
│ flightNumber│         │ flightId FK │         │ firstName   │
│ departure   │         │ seatNumber  │         │ lastName    │
│ arrival     │         │ state       │         │ email       │
│ aircraft    │         │ seatType    │         │ phone       │
│ status      │         │ price       │         └─────────────┘
└─────────────┘         │ heldBy   FK │               │
                        │ heldUntil   │               │
                        │ confirmedBy │               │
                        │ version     │               │
                        └─────────────┘               │
                               │                      │
                               └──────────────────────┘
                                         │
                        ┌────────────────┼────────────────┐
                        │                │                │
                   ┌─────────────┐  ┌─────────────┐  ┌─────────────┐
                   │  Check-In   │  │  Waitlist   │  │   Baggage   │
                   ├─────────────┤  ├─────────────┤  ├─────────────┤
                   │checkInId  PK│  │waitlistId PK│  │baggageId  PK│
                   │passengerId FK│  │passengerId FK│  │checkInId FK│
                   │flightId   FK│  │flightId   FK│  │weight       │
                   │seatId     FK│  │seatNumber   │  │unit         │
                   │status       │  │position     │  │type         │
                   │baggageFee   │  │joinedAt     │  │fee          │
                   │totalAmount  │  │status       │  └─────────────┘
                   │createdAt    │  └─────────────┘
                   │completedAt  │
                   └─────────────┘
```

### 8.2 Database Schema

#### Table: `flights`
```sql
CREATE TABLE flights (
  flight_id VARCHAR(20) PRIMARY KEY,
  flight_number VARCHAR(10) NOT NULL,
  departure_airport VARCHAR(3) NOT NULL,
  arrival_airport VARCHAR(3) NOT NULL,
  departure_time TIMESTAMP NOT NULL,
  arrival_time TIMESTAMP NOT NULL,
  aircraft_type VARCHAR(50) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_flight_number (flight_number),
  INDEX idx_departure_time (departure_time)
);
```

#### Table: `seats`
```sql
CREATE TABLE seats (
  seat_id BIGSERIAL PRIMARY KEY,
  flight_id VARCHAR(20) NOT NULL,
  seat_number VARCHAR(5) NOT NULL,
  state VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
  seat_type VARCHAR(20) NOT NULL,
  price DECIMAL(10,2) DEFAULT 0.00,
  held_by VARCHAR(20),
  held_until TIMESTAMP NULL,
  confirmed_by VARCHAR(20),
  confirmed_at TIMESTAMP NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  version INT DEFAULT 0,
  FOREIGN KEY (flight_id) REFERENCES flights(flight_id),
  FOREIGN KEY (held_by) REFERENCES passengers(passenger_id),
  FOREIGN KEY (confirmed_by) REFERENCES passengers(passenger_id),
  UNIQUE (flight_id, seat_number),
  INDEX idx_flight_state (flight_id, state),
  INDEX idx_held_until (held_until),
  CONSTRAINT chk_state CHECK (state IN ('AVAILABLE', 'HELD', 'CONFIRMED', 'CANCELLED'))
);
```

#### Table: `passengers`
```sql
CREATE TABLE passengers (
  passenger_id VARCHAR(20) PRIMARY KEY,
  first_name VARCHAR(100) NOT NULL,
  last_name VARCHAR(100) NOT NULL,
  email VARCHAR(255) NOT NULL,
  phone VARCHAR(20),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_email (email)
);
```

#### Table: `check_ins`
```sql
CREATE TABLE check_ins (
  check_in_id VARCHAR(20) PRIMARY KEY,
  passenger_id VARCHAR(20) NOT NULL,
  flight_id VARCHAR(20) NOT NULL,
  seat_id BIGINT NOT NULL,
  status VARCHAR(20) NOT NULL,
  baggage_fee DECIMAL(10,2) DEFAULT 0.00,
  total_amount DECIMAL(10,2) DEFAULT 0.00,
  payment_status VARCHAR(20) DEFAULT 'pending',
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  completed_at TIMESTAMP NULL,
  cancelled_at TIMESTAMP NULL,
  FOREIGN KEY (passenger_id) REFERENCES passengers(passenger_id),
  FOREIGN KEY (flight_id) REFERENCES flights(flight_id),
  FOREIGN KEY (seat_id) REFERENCES seats(seat_id),
  INDEX idx_passenger_flight (passenger_id, flight_id),
  INDEX idx_status (status),
  CONSTRAINT chk_status CHECK (status IN ('IN_PROGRESS', 'AWAITING_PAYMENT', 'COMPLETED', 'CANCELLED')),
  CONSTRAINT chk_payment_status CHECK (payment_status IN ('pending', 'paid', 'failed'))
);
```

#### Table: `baggage`
```sql
CREATE TABLE baggage (
  baggage_id BIGSERIAL PRIMARY KEY,
  check_in_id VARCHAR(20) NOT NULL,
  weight DECIMAL(5,2) NOT NULL,
  unit VARCHAR(5) DEFAULT 'kg',
  type VARCHAR(20) NOT NULL,
  fee DECIMAL(10,2) DEFAULT 0.00,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (check_in_id) REFERENCES check_ins(check_in_id),
  INDEX idx_check_in (check_in_id),
  CONSTRAINT chk_type CHECK (type IN ('carry_on', 'checked'))
);
```

#### Table: `waitlist`
```sql
CREATE TABLE waitlist (
  waitlist_id VARCHAR(20) PRIMARY KEY,
  passenger_id VARCHAR(20) NOT NULL,
  flight_id VARCHAR(20) NOT NULL,
  seat_number VARCHAR(5) NOT NULL,
  position INT NOT NULL,
  status VARCHAR(20) DEFAULT 'waiting',
  joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  notified_at TIMESTAMP NULL,
  assigned_at TIMESTAMP NULL,
  FOREIGN KEY (passenger_id) REFERENCES passengers(passenger_id),
  FOREIGN KEY (flight_id) REFERENCES flights(flight_id),
  INDEX idx_flight_seat_status (flight_id, seat_number, status),
  INDEX idx_position (position),
  CONSTRAINT chk_status CHECK (status IN ('waiting', 'notified', 'assigned', 'expired'))
);
```

#### Table: `audit_logs`
```sql
CREATE TABLE audit_logs (
  log_id BIGSERIAL PRIMARY KEY,
  entity_type VARCHAR(50) NOT NULL,
  entity_id VARCHAR(50) NOT NULL,
  action VARCHAR(50) NOT NULL,
  old_state JSONB,
  new_state JSONB,
  user_id VARCHAR(20),
  ip_address VARCHAR(45),
  timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_entity (entity_type, entity_id),
  INDEX idx_timestamp (timestamp)
);
```

### 8.3 Optimistic Locking for Concurrency

The `seats` table includes a `version` column for optimistic locking:

```sql
-- Example update with optimistic locking
UPDATE seats
SET state = 'HELD',
    held_by = 'P123456',
    held_until = NOW() + INTERVAL '120 seconds',
    version = version + 1,
    updated_at = NOW()
WHERE seat_id = 12345
  AND state = 'AVAILABLE'
  AND version = 5;

-- If affected rows = 0, seat was modified by another transaction
```

**JPA Entity Example**:
```java
@Entity
@Table(name = "seats")
public class Seat {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long seatId;
    
    @Version
    private Integer version;
    
    // Other fields...
}
```

---

## 9. Technology Stack

### 9.1 Backend
- **Language**: Java 17
- **Framework**: Spring Boot 3.2
- **Build Tool**: Maven
- **Database**: PostgreSQL 15
- **ORM**: Spring Data JPA (Hibernate)
- **Caching**: Caffeine (in-memory)
- **Rate Limiting**: Bucket4j
- **Scheduled Tasks**: Spring `@Scheduled`
- **Testing**: JUnit 5, Mockito, TestContainers

### 9.2 Frontend
- **Language**: TypeScript
- **Framework**: React 18
- **Build Tool**: Vite
- **State Management**: React Context / Zustand
- **HTTP Client**: Axios
- **UI Library**: Material-UI or Tailwind CSS
- **Testing**: Jest, React Testing Library

### 9.3 Infrastructure (AWS)
- **Compute**: EC2 (t3.medium)
- **Database**: RDS PostgreSQL (db.t3.micro)
- **Load Balancer**: Application Load Balancer (ALB)
- **Frontend Hosting**: S3 + CloudFront
- **IaC**: Terraform
- **Monitoring**: CloudWatch
- **Logging**: CloudWatch Logs

### 9.4 External Services

#### Mock/Stub Services
- **Weight Service**: Mock REST API for baggage validation
- **Payment Service**: Mock REST API for payment processing
- **Email Service**: AWS SES (Simple Email Service) for notifications

#### Real External APIs
- **AviationStack API**: Real-time flight status, gate information, and airport data
  - Free Tier: 100 API calls/month
  - Endpoint: https://api.aviationstack.com/v1/flights
  - Documentation: https://aviationstack.com/documentation

### 9.5 Development Tools
- **Version Control**: Git
- **CI/CD**: GitHub Actions
- **API Documentation**: Swagger/OpenAPI
- **Database Migrations**: Flyway

---

## 10. Out of Scope for MVP

The following features are explicitly **not included** in MVP:

### 10.1 Deferred to Future Releases

1. **Redis Caching**: Use in-memory caching for MVP, Redis when scaling
2. **Multi-passenger check-in**: Group bookings (family/friends)
3. **Priority tiers**: Gold/Platinum waitlist prioritization
4. **Multi-channel notifications**: Push/SMS (email only for MVP)
5. **Advanced rate limiting**: Distributed rate limiting with Redis
6. **Read replicas**: Single RDS instance for MVP
7. **Message queue**: RabbitMQ/SQS (use scheduled tasks instead)
8. **Advanced monitoring**: Prometheus/Grafana (CloudWatch only)
9. **Multi-language support**: English only in MVP
10. **OAuth 2.0**: Simple JWT authentication for MVP

### 10.2 Explicitly Excluded

1. **Flight booking**: This system handles check-in only
2. **Ticket pricing**: Dynamic pricing for seats
3. **Loyalty program**: Points, miles, status
4. **In-flight services**: Meal selection, entertainment
5. **Mobile app**: Web application only for MVP

---

## Appendix

### A. Glossary

| Term | Definition |
|------|------------|
| **ACID** | Atomicity, Consistency, Isolation, Durability |
| **FIFO** | First-In-First-Out queue ordering |
| **Idempotency** | Property where repeated operations produce same result |
| **JWT** | JSON Web Token - authentication standard |
| **P50/P95/P99** | Percentile latency metrics |
| **Optimistic Locking** | Concurrency control using version numbers |

### B. References

1. **PostgreSQL Concurrency Control**: https://www.postgresql.org/docs/current/mvcc.html
2. **Spring Boot Caching**: https://spring.io/guides/gs/caching/
3. **REST API Design Guidelines**: https://restfulapi.net/
4. **AviationStack API Documentation**: https://aviationstack.com/documentation

### C. Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-02-27 | SkyHigh Engineering | Initial MVP draft |
| 1.1 | 2026-02-27 | SkyHigh Engineering | Added FR-009: AviationStack API integration for real-time flight status |

---

**Document Status**: Draft  
**Next Review Date**: 2026-03-15  
**Contact**: skyhigh-core-team@skyhigh.com

---

*End of Product Requirements Document (MVP)*
