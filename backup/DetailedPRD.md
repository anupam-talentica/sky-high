# Product Requirements Document (PRD)
# SkyHigh Core – Digital Check-In System

**Version:** 1.0  
**Date:** February 27, 2026  
**Status:** Draft  
**Owner:** SkyHigh Airlines Engineering Team

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [Business Context](#2-business-context)
3. [Problem Statement](#3-problem-statement)
4. [Goals & Success Metrics](#4-goals--success-metrics)
5. [Functional Requirements](#5-functional-requirements)
6. [Non-Functional Requirements](#6-non-functional-requirements)
7. [System Architecture Overview](#7-system-architecture-overview)
8. [User Flows](#8-user-flows)
9. [API Specifications](#9-api-specifications)
10. [Data Models](#10-data-models)
11. [Security & Compliance](#11-security--compliance)
12. [Dependencies & Integrations](#12-dependencies--integrations)
13. [Constraints & Assumptions](#13-constraints--assumptions)
14. [Out of Scope](#14-out-of-scope)
15. [Risks & Mitigations](#15-risks--mitigations)
16. [Appendix](#16-appendix)

---

## 1. Executive Summary

SkyHigh Core is a backend service designed to revolutionize the airport self-check-in experience by providing a fast, reliable, and scalable digital check-in system. The system addresses critical challenges in managing high-concurrency seat selection, preventing conflicts, handling time-bound reservations, and detecting abusive access patterns during peak traffic periods.

### Key Capabilities
- **Conflict-free seat assignment** with guaranteed consistency
- **Time-bound seat reservations** (120-second hold mechanism)
- **Automated waitlist management** with real-time seat allocation
- **Baggage validation and payment integration**
- **High-performance seat map access** (P95 < 1 second)
- **Bot and abuse detection** with automated throttling

---

## 2. Business Context

### 2.1 Current Situation
SkyHigh Airlines operates in a highly competitive aviation market where customer experience directly impacts brand loyalty and revenue. The current check-in process faces significant challenges during peak hours:

- **High concurrent access**: Hundreds of passengers checking in simultaneously
- **Seat conflicts**: Manual or poorly synchronized systems leading to double bookings
- **Poor user experience**: Slow response times and system failures during rush hours
- **Revenue leakage**: Inefficient baggage fee collection and seat assignment

### 2.2 Business Drivers
- Improve operational efficiency during peak check-in windows
- Reduce customer service escalations related to seat conflicts
- Maximize ancillary revenue through seamless baggage fee collection
- Protect system resources from abuse and bot attacks
- Scale infrastructure to support business growth

### 2.3 Target Users
- **Primary**: Passengers checking in for flights (web and mobile)
- **Secondary**: Airport kiosk systems
- **Tertiary**: Customer service agents (for manual interventions)

---

## 3. Problem Statement

During popular flight check-in windows, the current system experiences:

1. **Race conditions** leading to duplicate seat assignments
2. **Indefinite seat holds** blocking availability for other passengers
3. **Poor performance** with response times exceeding 5 seconds during peak load
4. **Manual waitlist management** causing operational overhead
5. **Vulnerability to abuse** from automated bots scraping seat availability
6. **Inconsistent baggage fee enforcement** leading to revenue loss

**Impact**: Customer dissatisfaction, operational inefficiencies, and revenue loss.

---

## 4. Goals & Success Metrics

### 4.1 Primary Goals
1. Eliminate seat assignment conflicts with 100% consistency guarantee
2. Implement automated time-bound seat reservations (120 seconds)
3. Achieve P95 response time < 1 second for seat map access
4. Support 500+ concurrent users per flight without degradation
5. Automate waitlist assignment with zero manual intervention

### 4.2 Success Metrics

| Metric | Current State | Target | Measurement Method |
|--------|---------------|--------|-------------------|
| Seat conflict rate | ~2% of bookings | 0% | Database audit logs |
| Seat map P95 latency | 5-8 seconds | < 1 second | APM monitoring |
| Concurrent users supported | ~100 | 500+ | Load testing |
| Abandoned seat holds | Manual cleanup | 100% auto-released | System logs |
| Bot detection rate | 0% | 95%+ | Security monitoring |
| Baggage fee collection | 78% | 95%+ | Payment system integration |
| System uptime | 95% | 99.5% | Infrastructure monitoring |

### 4.3 Key Performance Indicators (KPIs)
- **Customer Satisfaction**: Reduce check-in related complaints by 60%
- **Revenue Impact**: Increase ancillary revenue by 15% through improved baggage fee collection
- **Operational Efficiency**: Reduce customer service escalations by 50%

---

## 5. Functional Requirements

### 5.1 Seat Availability & Lifecycle Management

#### 5.1.1 Seat State Machine

**Requirement ID**: FR-001  
**Priority**: P0 (Critical)

The system must implement a strict state machine for seat lifecycle management:

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

**Business Rules**:
1. A seat can only transition to `HELD` if current state is `AVAILABLE`
2. A seat in `HELD` state is exclusive to exactly one passenger
3. Only `CONFIRMED` seats can be `CANCELLED` by passengers
4. State transitions must be atomic and logged for audit purposes
5. Invalid state transitions must be rejected with appropriate error codes

**Acceptance Criteria**:
- ✅ System enforces state machine rules at database level
- ✅ Concurrent state transition attempts result in only one success
- ✅ All state changes are logged with timestamp and user ID
- ✅ Invalid transitions return HTTP 409 Conflict with clear error message

---

### 5.2 Time-Bound Seat Hold (120 Seconds)

#### 5.2.1 Seat Reservation Timer

**Requirement ID**: FR-002  
**Priority**: P0 (Critical)

When a passenger selects a seat, the system must:

1. **Reserve the seat** by transitioning state from `AVAILABLE` to `HELD`
2. **Start a 120-second countdown timer**
3. **Block other passengers** from reserving or confirming the same seat
4. **Automatically release** the seat if check-in is not completed within 120 seconds

**Technical Requirements**:
- Timer must be reliable and not dependent on client-side logic
- System must handle timer expiration even during server restarts
- Timer precision: ±2 seconds acceptable variance
- Expired holds must be cleaned up within 5 seconds of expiration

**Edge Cases**:
- If server crashes during hold period, seat must still be released after 120s
- If passenger completes check-in at exactly 120s, last-write-wins logic applies
- Multiple hold attempts on same seat must queue or fail gracefully

**Acceptance Criteria**:
- ✅ Seat automatically becomes `AVAILABLE` after 120 seconds if not confirmed
- ✅ System handles 1000+ concurrent timers without performance degradation
- ✅ Timer mechanism survives server restarts/crashes
- ✅ Passenger receives clear notification when hold is about to expire (at 30s remaining)

---

### 5.3 Conflict-Free Seat Assignment

#### 5.3.1 Concurrency Control

**Requirement ID**: FR-003  
**Priority**: P0 (Critical)

**Hard Guarantee**: If multiple passengers attempt to reserve the same seat simultaneously, exactly one reservation must succeed.

**Implementation Requirements**:
1. Use database-level optimistic or pessimistic locking
2. Implement idempotency for seat reservation requests
3. Return appropriate HTTP status codes:
   - `200 OK`: Reservation successful
   - `409 Conflict`: Seat already reserved
   - `423 Locked`: Seat temporarily unavailable (being processed)

**Consistency Requirements**:
- ACID compliance for all seat assignment transactions
- No eventual consistency for seat state
- Read-after-write consistency for seat availability queries

**Load Testing Requirements**:
- System must maintain consistency under 500 concurrent requests for same seat
- Zero duplicate assignments across 10,000 test iterations
- Performance degradation < 20% under maximum concurrent load

**Acceptance Criteria**:
- ✅ Load test with 500 concurrent users shows zero conflicts
- ✅ Database constraints prevent duplicate seat assignments
- ✅ Failed reservation attempts receive clear error messages
- ✅ System maintains consistency during network partitions (if distributed)

---

### 5.4 Passenger Cancellation

#### 5.4.1 Check-In Cancellation

**Requirement ID**: FR-004  
**Priority**: P1 (High)

Passengers must be able to cancel a confirmed check-in before flight departure.

**Business Rules**:
1. Only `CONFIRMED` seats can be cancelled
2. Cancellation is allowed up to 2 hours before scheduled departure
3. Cancelled seats immediately transition to `AVAILABLE`
4. If waitlist exists, seat is automatically assigned to next eligible passenger
5. Cancellation must trigger refund processing (if applicable)

**API Requirements**:
- `POST /api/v1/check-ins/{checkInId}/cancel`
- Idempotent operation (multiple cancel requests return same result)
- Returns cancellation confirmation with timestamp

**Notification Requirements**:
- Passenger receives cancellation confirmation via email/SMS
- If waitlisted passenger is assigned, they receive immediate notification

**Acceptance Criteria**:
- ✅ Cancelled seats become available within 1 second
- ✅ Waitlisted passengers are notified within 5 seconds
- ✅ Cancellation within 2-hour window is blocked with clear error
- ✅ Refund workflow is triggered automatically

---

### 5.5 Waitlist Management

#### 5.5.1 Automated Waitlist Assignment

**Requirement ID**: FR-005  
**Priority**: P1 (High)

When a seat is unavailable, passengers can join a waitlist. The system must automatically assign seats when they become available.

**Waitlist Rules**:
1. Passengers join waitlist in FIFO (First-In-First-Out) order
2. Priority passengers (frequent flyers, premium cabin) get preferential placement
3. When seat becomes `AVAILABLE`, system automatically:
   - Assigns seat to next eligible waitlisted passenger
   - Transitions seat to `HELD` for that passenger
   - Sends notification to passenger
   - Starts 120-second hold timer

**Priority Tiers**:
| Tier | Description | Queue Position Multiplier |
|------|-------------|---------------------------|
| Platinum | Top-tier frequent flyers | 3x |
| Gold | Mid-tier frequent flyers | 2x |
| Standard | Regular passengers | 1x |

**Notification Channels**:
- Push notification (mobile app)
- SMS (if mobile app not installed)
- Email (as backup)

**Edge Cases**:
- If notified passenger doesn't respond within 120s, move to next in waitlist
- If all waitlisted passengers are notified and don't respond, seat becomes publicly available
- Passengers can leave waitlist at any time

**Acceptance Criteria**:
- ✅ Waitlist assignment happens within 2 seconds of seat availability
- ✅ Priority passengers receive preferential treatment
- ✅ Notifications are sent via all configured channels
- ✅ System handles 100+ passengers on waitlist per flight

---

### 5.6 Baggage Validation & Payment Integration

#### 5.6.1 Baggage Weight Validation

**Requirement ID**: FR-006  
**Priority**: P1 (High)

During check-in, passengers may add baggage. The system must validate weight and enforce payment for overweight baggage.

**Business Rules**:
1. **Maximum allowed weight**: 25kg per bag
2. **Overweight threshold**: Any bag > 25kg
3. **Excess baggage fee**: $10 per kg over limit (configurable)

**Check-In Flow with Baggage**:
1. Passenger selects seat (seat moves to `HELD`)
2. Passenger adds baggage details
3. System calls **Weight Service** to validate baggage
4. If weight ≤ 25kg:
   - Continue to check-in confirmation
5. If weight > 25kg:
   - **Pause check-in** (seat remains `HELD`, timer continues)
   - Calculate excess fee
   - Display payment prompt
   - Call **Payment Service** to process fee
   - If payment succeeds: Resume check-in
   - If payment fails: Return to baggage selection
   - If timer expires during payment: Release seat, cancel check-in

**Check-In Status States**:
| Status | Description |
|--------|-------------|
| `IN_PROGRESS` | Passenger actively completing check-in |
| `AWAITING_PAYMENT` | Paused for baggage fee payment |
| `COMPLETED` | Check-in successfully confirmed |
| `CANCELLED` | Check-in cancelled or timed out |

**Integration Requirements**:
- **Weight Service**: `POST /api/v1/baggage/validate`
  - Input: `{ baggageWeight: number, unit: "kg" }`
  - Output: `{ isValid: boolean, excessWeight: number, fee: number }`
- **Payment Service**: `POST /api/v1/payments/process`
  - Input: `{ amount: number, currency: "USD", passengerId: string }`
  - Output: `{ transactionId: string, status: "success" | "failed" }`

**Acceptance Criteria**:
- ✅ Baggage over 25kg pauses check-in and prompts payment
- ✅ Seat hold timer continues during payment process
- ✅ Payment failure allows passenger to retry or modify baggage
- ✅ Check-in status accurately reflects current state
- ✅ System handles Weight Service and Payment Service timeouts gracefully

---

### 5.7 High-Performance Seat Map Access

#### 5.7.1 Seat Map Retrieval

**Requirement ID**: FR-007  
**Priority**: P0 (Critical)

Seat map browsing is the most frequently accessed feature and must perform exceptionally well under load.

**Performance Requirements**:
| Metric | Target | Measurement |
|--------|--------|-------------|
| P50 latency | < 300ms | APM monitoring |
| P95 latency | < 1 second | APM monitoring |
| P99 latency | < 2 seconds | APM monitoring |
| Concurrent users | 500+ per flight | Load testing |
| Throughput | 1000+ requests/sec | Load testing |

**Functional Requirements**:
1. Return seat map with real-time availability for a given flight
2. Include seat metadata:
   - Seat number (e.g., "12A")
   - Seat type (window, aisle, middle)
   - Current state (AVAILABLE, HELD, CONFIRMED)
   - Price (if premium seat)
   - Amenities (extra legroom, power outlet, etc.)
3. Support filtering by seat type, availability, price range
4. Implement efficient caching strategy:
   - Cache seat map structure (changes infrequently)
   - Real-time availability from database
   - Cache TTL: 60 seconds for availability data

**Optimization Strategies**:
- Database indexing on `flightId` and `seatState`
- Connection pooling for database queries
- CDN for static seat map layouts
- Redis/Memcached for availability caching
- Pagination for large aircraft (300+ seats)

**API Specification**:
```
GET /api/v1/flights/{flightId}/seat-map
Query params:
  - seatType: "window" | "aisle" | "middle"
  - availability: "available" | "all"
  - priceRange: "0-50" | "50-100" | "100+"
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
      price: number,
      amenities: string[]
    }
  ]
}
```

**Acceptance Criteria**:
- ✅ P95 latency < 1 second under 500 concurrent users
- ✅ Seat availability is accurate within 2 seconds of state change
- ✅ System handles 1000+ requests/sec without errors
- ✅ Caching reduces database load by 80%+

---

### 5.8 Abuse & Bot Detection

#### 5.8.1 Rate Limiting & Anomaly Detection

**Requirement ID**: FR-008  
**Priority**: P1 (High)

The system must detect and prevent abusive access patterns to protect resources and ensure fair access.

**Detection Criteria**:

**Scenario 1: Rapid Seat Map Access**
- **Trigger**: Single source (IP/user) accessing 50+ different seat maps within 2 seconds
- **Action**: 
  - Block further seat map requests for 5 minutes
  - Return HTTP 429 Too Many Requests
  - Log event with source IP, user ID, timestamp

**Scenario 2: Repeated Seat Hold Attempts**
- **Trigger**: Single user holding and releasing seats 10+ times within 1 minute
- **Action**:
  - Increase hold timer to 5 minutes for that user
  - Flag account for manual review
  - Log suspicious activity

**Scenario 3: Distributed Scraping**
- **Trigger**: Multiple IPs with similar user-agent patterns accessing seat maps
- **Action**:
  - Implement CAPTCHA challenge
  - Require authentication for seat map access
  - Log for security team review

**Rate Limiting Rules**:
| Endpoint | Limit | Window | Action on Exceed |
|----------|-------|--------|------------------|
| `GET /seat-map` | 20 requests | 10 seconds | 429 + 1 min cooldown |
| `POST /reserve-seat` | 5 requests | 1 minute | 429 + 5 min cooldown |
| `POST /check-in` | 3 requests | 5 minutes | 429 + 10 min cooldown |

**Implementation Requirements**:
- Use Redis for distributed rate limiting
- Track by IP address and authenticated user ID
- Implement sliding window algorithm for accurate rate limiting
- Whitelist internal IPs and partner systems

**Monitoring & Alerting**:
- Real-time dashboard showing rate limit violations
- Alert security team when threshold exceeded (100+ violations/hour)
- Weekly report of blocked IPs and suspicious patterns

**Acceptance Criteria**:
- ✅ System detects 50 seat map accesses in 2 seconds and blocks source
- ✅ Rate limits are enforced consistently across distributed system
- ✅ Legitimate users are not impacted by rate limiting
- ✅ Security team receives alerts for suspicious activity
- ✅ Blocked users receive clear error message with retry-after time

---

## 6. Non-Functional Requirements

### 6.1 Performance

**NFR-001: Response Time**
- P50 latency: < 300ms for all API endpoints
- P95 latency: < 1 second for seat map access
- P99 latency: < 2 seconds for check-in completion

**NFR-002: Throughput**
- Support 1000+ requests per second system-wide
- Handle 500+ concurrent users per flight
- Process 10,000+ check-ins per hour during peak times

**NFR-003: Resource Utilization**
- CPU utilization: < 70% under normal load
- Memory utilization: < 80% under normal load
- Database connection pool: < 80% utilization

### 6.2 Scalability

**NFR-004: Horizontal Scaling**
- System must scale horizontally by adding more application servers
- No single point of failure in architecture
- Stateless application tier (session data in Redis/database)

**NFR-005: Database Scaling**
- Support read replicas for seat map queries
- Implement database sharding strategy for 10M+ passengers
- Connection pooling with automatic scaling

**NFR-006: Load Balancing**
- Distribute traffic across multiple application instances
- Health checks every 10 seconds
- Automatic removal of unhealthy instances

### 6.3 Availability & Reliability

**NFR-007: Uptime**
- Target: 99.5% uptime (43.8 hours downtime per year)
- Planned maintenance windows: < 4 hours per month
- Zero-downtime deployments

**NFR-008: Disaster Recovery**
- Recovery Time Objective (RTO): < 1 hour
- Recovery Point Objective (RPO): < 5 minutes
- Automated backup every 6 hours
- Cross-region replication for critical data

**NFR-009: Fault Tolerance**
- Graceful degradation when external services fail
- Circuit breaker pattern for third-party integrations
- Retry logic with exponential backoff

### 6.4 Security

**NFR-010: Authentication & Authorization**
- OAuth 2.0 / JWT-based authentication
- Role-based access control (RBAC)
- API keys for partner integrations

**NFR-011: Data Encryption**
- TLS 1.3 for data in transit
- AES-256 encryption for sensitive data at rest
- Encrypted database backups

**NFR-012: Audit Logging**
- Log all seat state transitions with user ID and timestamp
- Retain audit logs for 7 years (compliance requirement)
- Tamper-proof logging mechanism

**NFR-013: Vulnerability Management**
- Regular security scans (weekly)
- Penetration testing (quarterly)
- Dependency vulnerability scanning in CI/CD pipeline

### 6.5 Observability

**NFR-014: Logging**
- Structured logging (JSON format)
- Log levels: DEBUG, INFO, WARN, ERROR, FATAL
- Centralized log aggregation (e.g., ELK stack)
- Log retention: 90 days

**NFR-015: Monitoring**
- Real-time metrics dashboard (Grafana/Datadog)
- Key metrics:
  - Request rate, error rate, latency (RED metrics)
  - CPU, memory, disk, network (infrastructure)
  - Database query performance
  - Cache hit/miss ratio
- Alerting on anomalies and threshold breaches

**NFR-016: Tracing**
- Distributed tracing for request flows (Jaeger/Zipkin)
- Trace all cross-service calls
- Correlation IDs for request tracking

### 6.6 Maintainability

**NFR-017: Code Quality**
- Test coverage: > 80%
- Code review required for all changes
- Automated linting and formatting
- Documentation for all public APIs

**NFR-018: Deployment**
- CI/CD pipeline with automated testing
- Blue-green or canary deployment strategy
- Automated rollback on failure
- Infrastructure as Code (Terraform/CloudFormation)

### 6.7 Compliance

**NFR-019: Data Privacy**
- GDPR compliance for EU passengers
- CCPA compliance for California passengers
- Data anonymization for analytics
- Right to deletion within 30 days

**NFR-020: Accessibility**
- WCAG 2.1 Level AA compliance (for frontend)
- API responses support screen readers
- Multi-language support (i18n)

---

## 7. System Architecture Overview

### 7.1 High-Level Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     Load Balancer (ALB)                      │
└──────────────────────────┬──────────────────────────────────┘
                           │
           ┌───────────────┼───────────────┐
           │               │               │
           ▼               ▼               ▼
    ┌──────────┐    ┌──────────┐    ┌──────────┐
    │   API    │    │   API    │    │   API    │
    │ Server 1 │    │ Server 2 │    │ Server N │
    └─────┬────┘    └─────┬────┘    └─────┬────┘
          │               │               │
          └───────────────┼───────────────┘
                          │
          ┌───────────────┼───────────────┐
          │               │               │
          ▼               ▼               ▼
    ┌──────────┐    ┌──────────┐    ┌──────────┐
    │  Redis   │    │ Primary  │    │  Queue   │
    │  Cache   │    │   DB     │    │ (RabbitMQ)│
    └──────────┘    └─────┬────┘    └──────────┘
                          │
                          ▼
                    ┌──────────┐
                    │ Read     │
                    │ Replicas │
                    └──────────┘

External Services:
┌──────────┐    ┌──────────┐    ┌──────────┐
│  Weight  │    │ Payment  │    │  Notify  │
│ Service  │    │ Service  │    │ Service  │
└──────────┘    └──────────┘    └──────────┘
```

### 7.2 Component Descriptions

**API Servers**:
- Stateless application servers (Node.js/Python/Java)
- Handle HTTP requests from clients
- Implement business logic and validation
- Communicate with database and external services

**Load Balancer**:
- Distributes traffic across API servers
- Health checks and automatic failover
- SSL termination

**Redis Cache**:
- Stores seat map data for fast access
- Rate limiting counters
- Session data (if needed)
- Distributed locks for concurrency control

**Primary Database**:
- PostgreSQL or MySQL
- ACID-compliant transactions
- Stores seat state, check-ins, passengers, flights
- Write operations

**Read Replicas**:
- Handles read-heavy queries (seat map access)
- Reduces load on primary database
- Eventual consistency acceptable for some queries

**Message Queue**:
- Asynchronous job processing
- Timer expiration handling
- Notification dispatch
- Waitlist processing

**External Services**:
- Weight Service: Baggage validation
- Payment Service: Fee processing
- Notification Service: Email/SMS/push notifications

### 7.3 Technology Stack Recommendations

| Layer | Technology Options |
|-------|-------------------|
| API Server | Node.js (Express), Python (FastAPI), Java (Spring Boot) |
| Database | PostgreSQL (recommended), MySQL |
| Cache | Redis (recommended), Memcached |
| Message Queue | RabbitMQ, AWS SQS, Apache Kafka |
| Load Balancer | AWS ALB, NGINX, HAProxy |
| Monitoring | Datadog, New Relic, Prometheus + Grafana |
| Logging | ELK Stack, Splunk, CloudWatch |
| Container Orchestration | Kubernetes, AWS ECS |

---

## 8. User Flows

### 8.1 Happy Path: Successful Check-In

```
Passenger                 System                  Database
    │                        │                        │
    │──1. GET /seat-map──────>│                        │
    │                        │──2. Query seats────────>│
    │                        │<──3. Return seats───────│
    │<──4. Seat map──────────│                        │
    │                        │                        │
    │──5. POST /reserve──────>│                        │
    │   (seat: 12A)          │──6. Lock seat──────────>│
    │                        │   (AVAILABLE→HELD)     │
    │                        │──7. Start 120s timer───>│
    │<──8. Reservation OK────│                        │
    │                        │                        │
    │──9. POST /baggage──────>│                        │
    │   (weight: 20kg)       │──10. Validate weight───>│
    │<──11. Validation OK────│    (Weight Service)    │
    │                        │                        │
    │──12. POST /confirm─────>│                        │
    │                        │──13. Update seat───────>│
    │                        │   (HELD→CONFIRMED)     │
    │<──14. Check-in done────│                        │
```

### 8.2 Overweight Baggage Flow

```
Passenger                 System                  Payment Service
    │                        │                        │
    │──1. POST /baggage──────>│                        │
    │   (weight: 30kg)       │──2. Validate───────────>│
    │                        │   (Weight Service)     │
    │                        │<──3. Excess: 5kg───────│
    │<──4. Payment required──│   Fee: $50             │
    │   (status: AWAITING)   │                        │
    │                        │                        │
    │──5. POST /payment──────>│                        │
    │   (amount: $50)        │──6. Process payment────>│
    │                        │<──7. Success───────────│
    │<──8. Payment confirmed─│                        │
    │                        │                        │
    │──9. POST /confirm──────>│                        │
    │                        │──10. Complete check-in─>│
    │<──11. Check-in done────│                        │
```

### 8.3 Seat Hold Timeout Flow

```
Passenger                 System                  Database
    │                        │                        │
    │──1. POST /reserve──────>│                        │
    │   (seat: 12A)          │──2. Lock seat──────────>│
    │                        │   (AVAILABLE→HELD)     │
    │<──3. Reservation OK────│──4. Start 120s timer───│
    │                        │                        │
    │                        │   ... 120 seconds ...  │
    │                        │                        │
    │                        │──5. Timer expires──────>│
    │                        │──6. Release seat───────>│
    │                        │   (HELD→AVAILABLE)     │
    │                        │──7. Notify passenger───>│
    │<──8. Seat released─────│   (Notification Svc)   │
```

### 8.4 Waitlist Assignment Flow

```
Passenger A              System                  Passenger B
    │                        │                        │
    │──1. POST /reserve──────>│                        │
    │   (seat: 12A)          │──2. Seat unavailable───│
    │<──3. Join waitlist?────│                        │
    │──4. POST /waitlist─────>│                        │
    │<──5. Waitlist #1───────│                        │
    │                        │                        │
    │                        │<──6. POST /cancel──────│
    │                        │   (seat: 12A)          │
    │                        │──7. Release seat───────>│
    │                        │   (CONFIRMED→AVAILABLE)│
    │                        │──8. Assign to A────────>│
    │                        │   (AVAILABLE→HELD)     │
    │<──9. Seat assigned─────│──10. Notify A──────────>│
    │   (120s to confirm)    │                        │
```

---

## 9. API Specifications

### 9.1 Base URL
```
Production: https://api.skyhigh.com/v1
Staging: https://api-staging.skyhigh.com/v1
```

### 9.2 Authentication
All API requests require authentication via JWT token in the `Authorization` header:
```
Authorization: Bearer <jwt_token>
```

### 9.3 Core Endpoints

#### 9.3.1 Get Seat Map
```
GET /flights/{flightId}/seat-map

Query Parameters:
  - seatType: string (optional) - "window" | "aisle" | "middle"
  - availability: string (optional) - "available" | "all"
  - priceRange: string (optional) - "0-50" | "50-100" | "100+"

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
      "price": 0,
      "amenities": ["power outlet"]
    }
  ]
}

Error Responses:
  404: Flight not found
  429: Rate limit exceeded
  500: Internal server error
```

#### 9.3.2 Reserve Seat
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

#### 9.3.3 Add Baggage
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

Error Responses:
  404: Check-in not found
  400: Invalid baggage data
```

#### 9.3.4 Process Payment
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

Error Responses:
  402: Payment failed
  404: Check-in not found
  400: Invalid payment data
```

#### 9.3.5 Confirm Check-In
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

Error Responses:
  404: Check-in not found
  409: Check-in already completed or expired
  402: Payment required
```

#### 9.3.6 Cancel Check-In
```
POST /check-ins/{checkInId}/cancel

Response 200:
{
  "checkInId": "C123456",
  "status": "CANCELLED",
  "seatNumber": "12A",
  "seatState": "AVAILABLE",
  "refundAmount": 0,
  "cancelledAt": "2026-02-27T10:35:00Z"
}

Error Responses:
  404: Check-in not found
  409: Cannot cancel within 2 hours of departure
```

#### 9.3.7 Join Waitlist
```
POST /flights/{flightId}/seats/{seatNumber}/waitlist

Request Body:
{
  "passengerId": "P123456",
  "priorityTier": "gold"
}

Response 200:
{
  "waitlistId": "W123456",
  "position": 3,
  "estimatedWaitTime": "15-30 minutes",
  "notificationChannels": ["push", "sms", "email"]
}

Error Responses:
  404: Flight or seat not found
  409: Already on waitlist
```

#### 9.3.8 Leave Waitlist
```
DELETE /waitlist/{waitlistId}

Response 204: No Content

Error Responses:
  404: Waitlist entry not found
```

### 9.4 Error Response Format
All error responses follow this structure:
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

## 10. Data Models

### 10.1 Entity Relationship Diagram

```
┌─────────────┐         ┌─────────────┐         ┌─────────────┐
│   Flight    │         │    Seat     │         │  Passenger  │
├─────────────┤         ├─────────────┤         ├─────────────┤
│ flightId PK │────────<│ seatId   PK │         │passengerId PK│
│ flightNumber│         │ flightId FK │         │ firstName   │
│ departure   │         │ seatNumber  │         │ lastName    │
│ arrival     │         │ state       │         │ email       │
│ aircraft    │         │ seatType    │         │ phone       │
│ status      │         │ price       │         │ priorityTier│
└─────────────┘         │ heldBy   FK │         └─────────────┘
                        │ heldUntil   │               │
                        │ confirmedBy │               │
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
                   │baggageFee   │  │priorityTier │  │fee          │
                   │totalAmount  │  │joinedAt     │  └─────────────┘
                   │createdAt    │  │notifiedAt   │
                   │completedAt  │  │status       │
                   └─────────────┘  └─────────────┘
```

### 10.2 Database Schema

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
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_flight_number (flight_number),
  INDEX idx_departure_time (departure_time)
);
```

#### Table: `seats`
```sql
CREATE TABLE seats (
  seat_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  flight_id VARCHAR(20) NOT NULL,
  seat_number VARCHAR(5) NOT NULL,
  state ENUM('AVAILABLE', 'HELD', 'CONFIRMED', 'CANCELLED') NOT NULL DEFAULT 'AVAILABLE',
  seat_type ENUM('window', 'aisle', 'middle') NOT NULL,
  price DECIMAL(10,2) DEFAULT 0.00,
  amenities JSON,
  held_by VARCHAR(20),
  held_until TIMESTAMP NULL,
  confirmed_by VARCHAR(20),
  confirmed_at TIMESTAMP NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  version INT DEFAULT 0,
  FOREIGN KEY (flight_id) REFERENCES flights(flight_id),
  FOREIGN KEY (held_by) REFERENCES passengers(passenger_id),
  FOREIGN KEY (confirmed_by) REFERENCES passengers(passenger_id),
  UNIQUE KEY unique_flight_seat (flight_id, seat_number),
  INDEX idx_flight_state (flight_id, state),
  INDEX idx_held_until (held_until)
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
  priority_tier ENUM('standard', 'gold', 'platinum') DEFAULT 'standard',
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
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
  status ENUM('IN_PROGRESS', 'AWAITING_PAYMENT', 'COMPLETED', 'CANCELLED') NOT NULL,
  baggage_fee DECIMAL(10,2) DEFAULT 0.00,
  total_amount DECIMAL(10,2) DEFAULT 0.00,
  payment_status ENUM('pending', 'paid', 'failed') DEFAULT 'pending',
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  completed_at TIMESTAMP NULL,
  cancelled_at TIMESTAMP NULL,
  FOREIGN KEY (passenger_id) REFERENCES passengers(passenger_id),
  FOREIGN KEY (flight_id) REFERENCES flights(flight_id),
  FOREIGN KEY (seat_id) REFERENCES seats(seat_id),
  INDEX idx_passenger_flight (passenger_id, flight_id),
  INDEX idx_status (status)
);
```

#### Table: `baggage`
```sql
CREATE TABLE baggage (
  baggage_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  check_in_id VARCHAR(20) NOT NULL,
  weight DECIMAL(5,2) NOT NULL,
  unit VARCHAR(5) DEFAULT 'kg',
  type ENUM('carry_on', 'checked') NOT NULL,
  fee DECIMAL(10,2) DEFAULT 0.00,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (check_in_id) REFERENCES check_ins(check_in_id),
  INDEX idx_check_in (check_in_id)
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
  priority_tier ENUM('standard', 'gold', 'platinum') DEFAULT 'standard',
  status ENUM('waiting', 'notified', 'assigned', 'expired') DEFAULT 'waiting',
  joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  notified_at TIMESTAMP NULL,
  assigned_at TIMESTAMP NULL,
  FOREIGN KEY (passenger_id) REFERENCES passengers(passenger_id),
  FOREIGN KEY (flight_id) REFERENCES flights(flight_id),
  INDEX idx_flight_seat_status (flight_id, seat_number, status),
  INDEX idx_position (position)
);
```

#### Table: `audit_logs`
```sql
CREATE TABLE audit_logs (
  log_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  entity_type VARCHAR(50) NOT NULL,
  entity_id VARCHAR(50) NOT NULL,
  action VARCHAR(50) NOT NULL,
  old_state JSON,
  new_state JSON,
  user_id VARCHAR(20),
  ip_address VARCHAR(45),
  timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_entity (entity_type, entity_id),
  INDEX idx_timestamp (timestamp)
);
```

#### Table: `rate_limits`
```sql
CREATE TABLE rate_limits (
  limit_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  identifier VARCHAR(100) NOT NULL,
  identifier_type ENUM('ip', 'user', 'api_key') NOT NULL,
  endpoint VARCHAR(255) NOT NULL,
  request_count INT DEFAULT 0,
  window_start TIMESTAMP NOT NULL,
  blocked_until TIMESTAMP NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY unique_identifier_endpoint (identifier, endpoint, window_start),
  INDEX idx_blocked_until (blocked_until)
);
```

### 10.3 Optimistic Locking for Concurrency

The `seats` table includes a `version` column for optimistic locking:

```sql
-- Example update with optimistic locking
UPDATE seats
SET state = 'HELD',
    held_by = 'P123456',
    held_until = NOW() + INTERVAL 120 SECOND,
    version = version + 1
WHERE seat_id = 12345
  AND state = 'AVAILABLE'
  AND version = 5;

-- If affected rows = 0, seat was modified by another transaction
```

---

## 11. Security & Compliance

### 11.1 Authentication & Authorization

**Authentication**:
- OAuth 2.0 with JWT tokens
- Token expiration: 1 hour
- Refresh token expiration: 7 days
- Multi-factor authentication (MFA) for high-value operations

**Authorization**:
- Role-Based Access Control (RBAC)
- Roles:
  - `passenger`: Can check-in for own flights
  - `agent`: Can assist passengers and override holds
  - `admin`: Full system access
  - `system`: For internal service-to-service calls

### 11.2 Data Protection

**PII (Personally Identifiable Information)**:
- Encrypt at rest: AES-256
- Encrypt in transit: TLS 1.3
- Mask in logs: `email: "j***@example.com"`
- Tokenize credit card data (PCI-DSS compliance)

**Data Retention**:
- Active check-ins: 90 days after flight
- Audit logs: 7 years
- Cancelled check-ins: 30 days
- Anonymize passenger data after retention period

### 11.3 Compliance Requirements

**GDPR (General Data Protection Regulation)**:
- Right to access: API endpoint to retrieve all passenger data
- Right to erasure: Delete passenger data within 30 days
- Data portability: Export passenger data in JSON format
- Consent management: Track and honor opt-in/opt-out preferences

**PCI-DSS (Payment Card Industry)**:
- Never store full credit card numbers
- Use tokenization for payment processing
- Annual security audit

**Accessibility**:
- WCAG 2.1 Level AA compliance for frontend
- API supports assistive technologies

---

## 12. Dependencies & Integrations

### 12.1 Internal Dependencies

| Service | Purpose | SLA | Fallback Strategy |
|---------|---------|-----|-------------------|
| Weight Service | Validate baggage weight | 99.5% uptime | Allow check-in, flag for manual review |
| Payment Service | Process baggage fees | 99.9% uptime | Queue payment, allow check-in |
| Notification Service | Send alerts to passengers | 99% uptime | Retry with exponential backoff |

### 12.2 External Dependencies

| Service | Purpose | Provider | SLA |
|---------|---------|----------|-----|
| Email Delivery | Send confirmation emails | SendGrid/AWS SES | 99.9% |
| SMS Delivery | Send text notifications | Twilio | 99.95% |
| Push Notifications | Mobile app alerts | Firebase/APNs | 99.9% |

### 12.3 Integration Patterns

**Circuit Breaker**:
- Open circuit after 5 consecutive failures
- Half-open after 30 seconds
- Close after 3 successful requests

**Retry Logic**:
- Exponential backoff: 1s, 2s, 4s, 8s
- Maximum 3 retries
- Only retry idempotent operations

**Timeouts**:
- Weight Service: 2 seconds
- Payment Service: 5 seconds
- Notification Service: 3 seconds

---

## 13. Constraints & Assumptions

### 13.1 Constraints

**Technical Constraints**:
1. Database must support ACID transactions
2. Seat hold timer precision: ±2 seconds acceptable
3. System must run on cloud infrastructure (AWS/Azure/GCP)
4. API must be RESTful (no GraphQL in v1)

**Business Constraints**:
1. Seat hold duration fixed at 120 seconds (non-configurable in v1)
2. Maximum baggage weight: 25kg (regulatory requirement)
3. Check-in opens 24 hours before departure
4. Check-in closes 2 hours before departure

**Operational Constraints**:
1. Deployment window: 2 AM - 4 AM local time
2. Maintenance window: < 4 hours per month
3. Database backup window: 3 AM daily

### 13.2 Assumptions

**System Assumptions**:
1. Passengers have unique identifiers (passenger_id)
2. Flights are pre-loaded into the system
3. Seat maps are configured before check-in opens
4. Network latency between services < 50ms

**Business Assumptions**:
1. Average check-in duration: 3-5 minutes
2. Peak traffic: 500 concurrent users per popular flight
3. 80% of passengers check-in within 6 hours of departure
4. 10% of passengers require baggage fee payment

**User Assumptions**:
1. Passengers have internet access
2. Passengers use modern browsers (last 2 versions)
3. Passengers understand English (i18n in future release)

---

## 14. Out of Scope

The following features are explicitly **not included** in v1:

### 14.1 Deferred to Future Releases

1. **Multi-passenger check-in**: Group bookings (family/friends)
2. **Seat preferences**: Automatic seat recommendation based on history
3. **Upgrade bidding**: Passengers bidding for premium seats
4. **Mobile boarding pass**: QR code generation (v1 uses barcode only)
5. **Offline mode**: Check-in without internet connection
6. **Third-party integrations**: Travel agency APIs, loyalty programs
7. **Advanced analytics**: ML-based demand forecasting
8. **Multi-language support**: i18n (English only in v1)
9. **Accessibility features**: Screen reader optimization (basic support only)
10. **Special assistance**: Wheelchair, unaccompanied minors

### 14.2 Explicitly Excluded

1. **Flight booking**: This system handles check-in only, not reservations
2. **Ticket pricing**: Dynamic pricing for seats
3. **Loyalty program management**: Points, miles, status
4. **In-flight services**: Meal selection, entertainment
5. **Post-flight**: Baggage tracking, feedback surveys

---

## 15. Risks & Mitigations

### 15.1 Technical Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Database deadlocks under high concurrency | Medium | High | Implement optimistic locking, connection pooling, query optimization |
| Timer mechanism fails during server restart | Low | High | Use database-backed timers or distributed job queue |
| Cache invalidation issues | Medium | Medium | Implement cache-aside pattern with TTL, monitor cache hit rate |
| Third-party service outage | Medium | Medium | Circuit breaker, fallback logic, queue for retry |
| DDoS attack | Low | High | Rate limiting, WAF, CDN with DDoS protection |

### 15.2 Business Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Passenger dissatisfaction with 120s timer | Medium | Medium | User testing, clear UI countdown, extend timer if needed |
| Revenue loss from baggage fee bypass | Low | High | Strict validation, audit logging, manual review process |
| Regulatory compliance failure | Low | Critical | Legal review, compliance audit, automated checks |
| Competitive pressure | High | Medium | Continuous improvement, feature roadmap, user feedback |

### 15.3 Operational Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Insufficient load testing | Medium | High | Comprehensive load testing before launch, gradual rollout |
| Inadequate monitoring | Medium | High | Implement observability from day 1, on-call rotation |
| Data migration issues | Low | High | Dry-run migrations, rollback plan, data validation scripts |
| Vendor lock-in | Medium | Medium | Use open standards, abstract vendor-specific APIs |

---

## 16. Appendix

### 16.1 Glossary

| Term | Definition |
|------|------------|
| **ACID** | Atomicity, Consistency, Isolation, Durability - database transaction properties |
| **APM** | Application Performance Monitoring |
| **Circuit Breaker** | Design pattern to prevent cascading failures |
| **FIFO** | First-In-First-Out queue ordering |
| **Idempotency** | Property where repeated operations produce same result |
| **JWT** | JSON Web Token - authentication standard |
| **P50/P95/P99** | Percentile latency metrics (50th, 95th, 99th percentile) |
| **RBAC** | Role-Based Access Control |
| **RTO** | Recovery Time Objective |
| **RPO** | Recovery Point Objective |
| **TTL** | Time To Live - cache expiration time |

### 16.2 References

1. **Industry Standards**:
   - IATA Passenger Services Conference Resolutions
   - PCI-DSS Payment Security Standards
   - GDPR Data Protection Regulation

2. **Technical Documentation**:
   - PostgreSQL Concurrency Control: https://www.postgresql.org/docs/current/mvcc.html
   - Redis Distributed Locks: https://redis.io/docs/manual/patterns/distributed-locks/
   - Circuit Breaker Pattern: https://martinfowler.com/bliki/CircuitBreaker.html

3. **Best Practices**:
   - REST API Design Guidelines
   - Microservices Patterns (Chris Richardson)
   - Site Reliability Engineering (Google)

### 16.3 Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-02-27 | SkyHigh Engineering | Initial draft |

### 16.4 Approval Sign-Off

| Role | Name | Signature | Date |
|------|------|-----------|------|
| Product Manager | TBD | | |
| Engineering Lead | TBD | | |
| Security Officer | TBD | | |
| Compliance Officer | TBD | | |

---

**Document Status**: Draft  
**Next Review Date**: 2026-03-15  
**Contact**: skyhigh-core-team@skyhigh.com

---

*End of Product Requirements Document*
