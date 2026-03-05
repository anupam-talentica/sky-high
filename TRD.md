# Technical Requirements Document (TRD)
# SkyHigh Core – Digital Check-In System

**Version:** 1.0 (MVP)  
**Date:** February 27, 2026  
**Status:** Draft  
**Owner:** SkyHigh Airlines Engineering Team

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [Architecture Overview](#2-architecture-overview)
3. [Technology Stack](#3-technology-stack)
4. [Infrastructure Design](#4-infrastructure-design)
5. [Database Design](#5-database-design)
6. [Security & Authentication](#6-security--authentication)
7. [Caching Strategy](#7-caching-strategy)
8. [Background Jobs & Timers](#8-background-jobs--timers)
9. [External Services](#9-external-services)
10. [Deployment Strategy](#10-deployment-strategy)
11. [Monitoring & Logging](#11-monitoring--logging)
12. [Testing Strategy](#12-testing-strategy)
13. [Migration Path](#13-migration-path)
14. [Appendix](#14-appendix)

---

## 1. Executive Summary

This Technical Requirements Document (TRD) defines the implementation approach for SkyHigh Core MVP - a digital check-in system designed for simplicity, cost-effectiveness, and future scalability.

### 1.1 Key Design Decisions

| Decision Area | MVP Choice | Rationale |
|--------------|------------|-----------|
| **Infrastructure** | Single EC2 with Docker | Cost-effective, simple deployment |
| **Database** | PostgreSQL in Docker | Managed with backend, easy migration to RDS later |
| **Caching** | In-memory (Caffeine) | Configurable, zero infrastructure overhead |
| **Authentication** | Simple JWT | Sufficient for MVP, easy to upgrade |
| **Frontend Hosting** | S3 + CloudFront | Industry standard, scalable, production-ready |
| **Load Balancer** | None (direct EC2) | Cost savings, add later when scaling |
| **External Services** | Mock/Stub | Focus on core logic |
| **Monitoring** | CloudWatch | AWS native, minimal setup |
| **Database Migrations** | Flyway | Version control for schema |
| **Testing** | Unit tests (80% coverage) | Quality assurance |

### 1.2 Cost Optimization

**MVP Monthly Cost: ~$43-48**
- EC2 (t3.medium): ~$30
- EBS Volume (30 GB): ~$3
- S3 + CloudFront: ~$5-10
- CloudWatch: ~$5

**Savings vs Full Setup: ~$63/month** (No RDS, No ALB, No NAT Gateway)

---

## 2. Architecture Overview

### 2.1 High-Level Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                       Internet                               │
└────────────────────────┬────────────────────────────────────┘
                         │
        ┌────────────────┼────────────────┐
        │                │                │
        ▼                ▼                ▼
   ┌─────────┐    ┌─────────────┐   ┌─────────┐
   │CloudFront│   │  Elastic IP │   │  Users  │
   │   CDN    │   │   (Public)  │   │ Browser │
   └────┬────┘    └──────┬──────┘   └─────────┘
        │                │
        ▼                ▼
   ┌─────────┐    ┌──────────────────────────────┐
   │   S3    │    │      EC2 Instance            │
   │ Bucket  │    │      (t3.medium)             │
   │         │    │                              │
   │ React   │    │  Docker Compose:             │
   │  App    │    │  ├─ Spring Boot (Port 80)    │
   └─────────┘    │  └─ PostgreSQL (Port 5432)   │
                  │                              │
                  │  EBS Volume (30 GB)          │
                  └──────────────────────────────┘
                         │
                         ▼
                  ┌──────────────┐
                  │  CloudWatch  │
                  │ Logs/Metrics │
                  └──────────────┘
```

### 2.2 Component Responsibilities

| Component | Responsibilities | Technology |
|-----------|-----------------|------------|
| **Frontend** | UI/UX, user interactions, API calls | React 18 + TypeScript |
| **Backend** | Business logic, API endpoints, caching | Spring Boot 3.2 + Java 17 |
| **Database** | Data persistence, ACID transactions | PostgreSQL 15 |
| **CDN** | Static asset delivery, HTTPS | CloudFront |
| **Storage** | Frontend hosting | S3 |
| **Monitoring** | Logs, metrics, alerts | CloudWatch |

### 2.3 Request Flow

```
User → CloudFront (Frontend) → EC2 (Backend API) → PostgreSQL → Response
                                    ↓
                              In-Memory Cache
                              (Caffeine)
                                    ↓
                           External APIs:
                           - AviationStack (Flight Status)
                           - Mock Services (Weight, Payment)
```

### 2.4 Network Configuration

**VPC**: 10.0.0.0/16  
**Public Subnet**: 10.0.1.0/24  
**EC2 Instance**: Elastic IP attached  
**Security Group**:
- Inbound: 80 (HTTP), 443 (HTTPS), 22 (SSH from specific IP)
- Outbound: All traffic allowed

**Docker Network**: Bridge network for container communication

---

## 3. Technology Stack

### 3.1 Backend Stack

| Component | Technology | Version |
|-----------|-----------|---------|
| **Language** | Java | 17 LTS |
| **Framework** | Spring Boot | 3.2.x |
| **Build Tool** | Maven | 3.9.x |
| **Database** | PostgreSQL | 15 |
| **ORM** | Spring Data JPA | 3.2.x |
| **Migration** | Flyway | 9.x |
| **Caching** | Caffeine (configurable to Redis) | 3.1.x |
| **Security** | Spring Security + JWT | 6.2.x |
| **Testing** | JUnit 5 + Mockito | 5.10.x |
| **API Docs** | SpringDoc OpenAPI | 2.3.x |
| **HTTP Client** | RestTemplate / WebClient | Spring 6.x |
| **Resilience** | Resilience4j (Circuit Breaker) | 2.1.x |

### 3.2 Frontend Stack

| Component | Technology | Version |
|-----------|-----------|---------|
| **Language** | TypeScript | 5.x |
| **Framework** | React | 18.x |
| **Build Tool** | Vite | 5.x |
| **State Management** | Zustand | 4.x |
| **HTTP Client** | Axios | 1.6.x |
| **UI Library** | Material-UI (MUI) | 5.x |
| **Routing** | React Router | 6.x |

### 3.3 Infrastructure Stack

| Component | Technology | Purpose |
|-----------|-----------|---------|
| **Cloud Provider** | AWS | Infrastructure hosting |
| **Compute** | EC2 (t3.medium) | Application server |
| **Storage** | EBS (gp3, 30 GB) | Block storage |
| **Frontend Hosting** | S3 + CloudFront | Static site hosting |
| **SSL/TLS** | AWS ACM | Certificate management |
| **Monitoring** | CloudWatch | Logs and metrics |
| **IaC** | Terraform | Infrastructure as Code |
| **Container Runtime** | Docker Compose | Container orchestration |

### 3.4 Key Backend Dependencies (Maven)

**Spring Boot Starters**:
```xml
<!-- Core Framework -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>

<!-- Database -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>

<!-- Security -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>

<!-- Validation -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>

<!-- Actuator for Health Checks -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

**Database & Migrations**:
```xml
<!-- PostgreSQL Driver -->
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>runtime</scope>
</dependency>

<!-- Flyway for Database Migrations -->
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
</dependency>
```

**Caching**:
```xml
<!-- Caffeine Cache -->
<dependency>
    <groupId>com.github.ben-manes.caffeine</groupId>
    <artifactId>caffeine</artifactId>
</dependency>

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-cache</artifactId>
</dependency>
```

**Resilience & External API Integration**:
```xml
<!-- Resilience4j for Circuit Breaker -->
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-spring-boot3</artifactId>
    <version>2.1.0</version>
</dependency>

<!-- For AviationStack API Integration -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
</dependency>
```

**Rate Limiting**:
```xml
<!-- Bucket4j for Rate Limiting -->
<dependency>
    <groupId>com.github.vladimir-bukhtoyarov</groupId>
    <artifactId>bucket4j-core</artifactId>
    <version>8.7.0</version>
</dependency>
```

**JWT Authentication**:
```xml
<!-- JWT Support -->
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.3</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.12.3</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.12.3</version>
    <scope>runtime</scope>
</dependency>
```

**API Documentation**:
```xml
<!-- SpringDoc OpenAPI (Swagger) -->
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.3.0</version>
</dependency>
```

**Testing**:
```xml
<!-- Spring Boot Test -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>

<!-- TestContainers for Integration Tests -->
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <version>1.19.3</version>
    <scope>test</scope>
</dependency>
```

**Utilities**:
```xml
<!-- Lombok for Boilerplate Reduction -->
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <optional>true</optional>
</dependency>
```

---

## 4. Infrastructure Design

### 4.1 AWS Resources

#### 4.1.1 EC2 Instance

| Specification | Value |
|--------------|-------|
| **Instance Type** | t3.medium (2 vCPU, 4 GB RAM) |
| **Operating System** | Amazon Linux 2023 |
| **EBS Volume** | 30 GB gp3 SSD, encrypted |
| **Elastic IP** | Yes (static public IP) |
| **Docker** | Docker Engine 24.x + Docker Compose 2.x |
| **CloudWatch Agent** | Installed for metrics/logs |

#### 4.1.2 Security Group

| Direction | Port | Source/Destination | Purpose |
|-----------|------|-------------------|---------|
| Inbound | 80 | 0.0.0.0/0 | HTTP access |
| Inbound | 443 | 0.0.0.0/0 | HTTPS access |
| Inbound | 22 | YOUR_IP/32 | SSH management |
| Outbound | All | 0.0.0.0/0 | All outbound traffic |

#### 4.1.3 S3 + CloudFront

**S3 Bucket**:
- Name: `skyhigh-frontend-<account-id>`
- Versioning: Enabled
- Encryption: AES-256
- Public Access: Blocked (CloudFront only)
- Static Website Hosting: Enabled

**CloudFront Distribution**:
- Origin: S3 bucket
- Viewer Protocol: Redirect HTTP to HTTPS
- SSL Certificate: AWS ACM
- Custom Error Responses: 403/404 → 200 (for SPA routing)

### 4.2 Docker Compose Overview

**Services**:
1. **PostgreSQL Container**
   - Image: `postgres:15-alpine`
   - Port: 5432
   - Volume: Persistent data storage
   - Health Check: `pg_isready`
   - Resources: 1 CPU, 1 GB RAM

2. **Backend Container**
   - Image: `skyhigh/backend:latest`
   - Port: 8080 (mapped to host 80)
   - Depends on: PostgreSQL (health check)
   - Health Check: `/actuator/health`
   - Resources: 1.5 CPU, 2 GB RAM

**Network**: Bridge network for container communication

**Volumes**: `postgres-data` for database persistence

---

## 5. Database Design

### 5.1 Database Schema Overview

**Tables**:
1. `flights` - Flight information
2. `passengers` - Passenger details (with hardcoded users)
3. `seats` - Seat inventory with state machine
4. `check_ins` - Check-in records
5. `baggage` - Baggage information
6. `waitlist` - Waitlist entries
7. `audit_logs` - Audit trail for state changes

### 5.2 Key Design Patterns

#### 5.2.1 Optimistic Locking

**Seats Table** includes `version` column for concurrency control:
- JPA `@Version` annotation
- Automatic version increment on update
- Prevents race conditions during seat reservation
- Throws `OptimisticLockException` on conflict

#### 5.2.2 State Machine Enforcement

**Seat States**: `AVAILABLE` → `HELD` → `CONFIRMED` → `CANCELLED`

**Constraints**:
- Database CHECK constraint on state column
- Application-level validation in entity
- Audit logging for all state transitions

#### 5.2.3 Indexes

| Table | Index | Columns | Purpose |
|-------|-------|---------|---------|
| `seats` | `idx_flight_state` | flight_id, state | Fast seat availability queries |
| `seats` | `idx_held_until` | held_until | Efficient expiration checks |
| `seats` | `unique_flight_seat` | flight_id, seat_number | Prevent duplicates |
| `check_ins` | `idx_passenger_flight` | passenger_id, flight_id | User check-in lookup |
| `waitlist` | `idx_flight_seat_status` | flight_id, seat_number, status | Waitlist queries |

### 5.3 Flyway Migrations

**Migration Files** (in `src/main/resources/db/migration/`):
- `V1__create_flights_table.sql`
- `V2__create_passengers_table.sql`
- `V3__create_seats_table.sql` (with version column)
- `V4__create_check_ins_table.sql`
- `V5__create_baggage_table.sql`
- `V6__create_waitlist_table.sql`
- `V7__create_audit_logs_table.sql`
- `V8__insert_sample_data.sql` (hardcoded users + sample flight)

**Configuration**:
- Flyway enabled by default
- Baseline on migrate for existing databases
- Migrations run automatically on application startup

### 5.4 Sample Data (MVP)

**Hardcoded Passengers**:
- P123456: john@example.com (password: demo123)
- P789012: jane@example.com (password: demo456)

**Sample Flight**:
- Flight ID: SK1234
- Route: JFK → LAX
- Aircraft: Boeing 737-800 (189 seats)

### 5.5 Connection Pooling

**HikariCP Configuration**:
- Maximum pool size: 20
- Minimum idle: 5
- Connection timeout: 30 seconds
- Idle timeout: 10 minutes
- Max lifetime: 30 minutes

---

## 6. Security & Authentication

### 6.1 Authentication Flow

```
1. User Login
   ├─ POST /api/v1/auth/login
   ├─ Validate credentials (hardcoded for MVP)
   ├─ Generate JWT token (1 hour expiration)
   └─ Return token + passenger info

2. API Request
   ├─ Include JWT in Authorization header
   ├─ JwtAuthenticationFilter validates token
   ├─ Extract passenger ID from token
   └─ Proceed with request

3. Token Expiration
   ├─ Token expires after 1 hour
   ├─ Frontend detects 401 response
   └─ Redirect to login page
```

### 6.2 JWT Configuration

| Parameter | Value |
|-----------|-------|
| **Algorithm** | HS512 |
| **Secret** | Environment variable (min 256 bits) |
| **Expiration** | 1 hour (3600000 ms) |
| **Refresh Token** | Not implemented in MVP |

### 6.3 Hardcoded Users (MVP)

Stored in `application.yml` or database:
- Passenger ID, email, hashed password
- BCrypt password hashing
- Easy to migrate to database-driven auth later

### 6.4 Security Best Practices

1. **HTTPS Only**: All API communication over TLS 1.3
2. **CORS**: Configure allowed origins (frontend URL only)
3. **Input Validation**: Validate all request parameters
4. **SQL Injection**: Prevented by JPA parameterized queries
5. **Rate Limiting**: In-memory rate limiting with Bucket4j
6. **Password Hashing**: BCrypt with salt

---

## 7. Caching Strategy

### 7.1 Cache Configuration (Configurable)

**Default: Caffeine (In-Memory)**
- Technology: Caffeine cache
- Configuration: `spring.cache.type=caffeine`
- TTL: 60 seconds
- Max size: 500 entries
- Zero infrastructure overhead

**Future: Redis (Distributed)**
- Technology: Redis ElastiCache
- Configuration: `spring.cache.type=redis`
- Shared across multiple instances
- Requires infrastructure setup

### 7.2 Cache Strategy

| Cache Name | Data | TTL | Eviction Policy |
|-----------|------|-----|-----------------|
| `seatMaps` | Seat map for a flight | 60 seconds | Write-through |
| `flights` | Flight details | 5 minutes | Write-through |

### 7.3 Cache Invalidation

**Triggers**:
- Seat state change (AVAILABLE → HELD → CONFIRMED)
- Flight update
- Manual cache clear

**Implementation**:
- `@CacheEvict` annotation on update methods
- Invalidate specific flight's cache
- Immediate consistency for critical operations

### 7.4 Migration to Redis

**When to migrate**:
- Scaling to multiple EC2 instances
- Traffic exceeds 1000 concurrent users
- Need distributed rate limiting

**Migration steps**:
1. Add Redis dependency to `pom.xml`
2. Update `spring.cache.type=redis` in configuration
3. No code changes required (Spring Boot handles switch)

---

## 8. Background Jobs & Timers

### 8.1 Seat Expiration Timer

**Implementation**: Spring `@Scheduled` task

**Configuration**:
- Runs every 5 seconds
- Queries database for expired seats: `held_until < NOW() AND state = 'HELD'`
- Transitions expired seats to `AVAILABLE`
- Invalidates cache for affected flights

**Reliability**:
- Database-backed timer state (survives restarts)
- Timer precision: ±2 seconds acceptable
- Handles 1000+ concurrent timers

**Code Location**: `SeatExpirationScheduler.java`

### 8.2 Waitlist Processing

**Trigger**: When seat becomes `AVAILABLE`

**Process**:
1. Check if waitlist exists for flight/seat
2. Assign to next passenger in FIFO order
3. Transition seat to `HELD` for waitlisted passenger
4. Send notification (email via mock service)
5. Start 120-second hold timer

---

## 9. External Services

### 9.1 Mock Services (MVP)

The following services are mocked/stubbed for MVP:

#### 9.1.1 Weight Service

**Purpose**: Validate baggage weight

**Mock Implementation**:
- Simple in-memory validation
- Returns excess weight and fee calculation
- Configurable max weight (25 kg)

**API**: `POST /api/v1/baggage/validate`

#### 9.1.2 Payment Service

**Purpose**: Process baggage fee payments

**Mock Implementation**:
- Simulates payment processing
- Configurable success/failure rate
- Generates mock transaction ID

**API**: `POST /api/v1/payments/process`

#### 9.1.3 Notification Service

**Purpose**: Send email notifications

**MVP Implementation**:
- AWS SES for real email delivery
- Simple email templates
- Async processing

**API**: Internal service call

---

### 9.2 Real External API Integration

#### 9.2.1 AviationStack API (Flight Status Service)

**Purpose**: Fetch real-time flight status, gate information, and airport data

**Provider**: AviationStack (https://aviationstack.com/)

**API Details**:
- **Base URL**: `http://api.aviationstack.com/v1`
- **Authentication**: API key via query parameter `access_key`
- **Free Tier**: 100 API calls/month
- **Paid Tier**: 500 calls/month at $9.99 (Basic Plan)

**Endpoints Used**:

1. **Get Flight Status**
```
GET /flights?flight_iata={flightNumber}&access_key={API_KEY}

Response:
{
  "data": [
    {
      "flight_date": "2026-02-27",
      "flight_status": "scheduled",
      "departure": {
        "airport": "John F Kennedy International",
        "timezone": "America/New_York",
        "iata": "JFK",
        "terminal": "4",
        "gate": "B12",
        "scheduled": "2026-02-27T14:30:00+00:00"
      },
      "arrival": {
        "airport": "Los Angeles International",
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
  ]
}
```

**Implementation Details**:

**Service Layer** (`FlightStatusService.java`):
```java
@Service
public class FlightStatusService {
    
    private final RestTemplate restTemplate;
    private final String apiKey;
    private final String baseUrl = "http://api.aviationstack.com/v1";
    
    @Cacheable(value = "flightStatus", key = "#flightNumber", unless = "#result == null")
    public FlightStatusResponse getFlightStatus(String flightNumber) {
        String url = String.format("%s/flights?flight_iata=%s&access_key=%s", 
            baseUrl, flightNumber, apiKey);
        
        try {
            AviationStackResponse response = restTemplate.getForObject(url, AviationStackResponse.class);
            return mapToFlightStatus(response);
        } catch (Exception e) {
            log.error("Failed to fetch flight status from AviationStack", e);
            return getFallbackFlightStatus(flightNumber);
        }
    }
    
    private FlightStatusResponse getFallbackFlightStatus(String flightNumber) {
        // Fallback to local database
        return flightRepository.findByFlightNumber(flightNumber)
            .map(this::mapToFlightStatus)
            .orElseThrow(() -> new FlightNotFoundException(flightNumber));
    }
}
```

**Caching Strategy**:
- Cache flight status for 5 minutes
- Cache key: `flightStatus:{flightNumber}`
- Use Caffeine in-memory cache for MVP
- TTL: 300 seconds (5 minutes)
- Reduces API calls by ~80%

**Error Handling**:
1. **API Unavailable**: Fallback to local database flight information
2. **Rate Limit Exceeded**: Use cached data, log warning
3. **Flight Not Found**: Return 404 to client
4. **Timeout**: 5-second timeout, then fallback

**Circuit Breaker Configuration**:
```yaml
resilience4j:
  circuitbreaker:
    instances:
      aviationstack:
        failure-rate-threshold: 50
        wait-duration-in-open-state: 60000
        sliding-window-size: 10
        minimum-number-of-calls: 5
```

**Retry Configuration**:
```yaml
resilience4j:
  retry:
    instances:
      aviationstack:
        max-attempts: 3
        wait-duration: 1000
        exponential-backoff-multiplier: 2
```

**Rate Limiting Strategy**:
- Free tier: 100 calls/month = ~3 calls/day
- Cache aggressively to stay within limits
- Only fetch on check-in initiation (not on every seat selection)
- Monitor usage via CloudWatch metrics

**Configuration** (`application.yml`):
```yaml
external:
  aviationstack:
    api-key: ${AVIATIONSTACK_API_KEY}
    base-url: http://api.aviationstack.com/v1
    timeout: 5000
    cache-ttl: 300
    enabled: true
```

**Acceptance Criteria**:
- ✅ Flight status fetched within 2 seconds (P95)
- ✅ Fallback to local database if API unavailable
- ✅ Caching reduces API calls by 80%+
- ✅ Circuit breaker prevents cascading failures
- ✅ Stays within free tier limits (100 calls/month)

---

### 9.3 Integration Points

**Design Principles**:
- Clear service interfaces with dependency injection
- Easy to replace mocks with real implementations
- Circuit breaker pattern for resilience (Resilience4j)
- Retry logic with exponential backoff
- Graceful degradation when services unavailable
- Comprehensive logging of all external calls
- Metrics tracking for API performance and failures

---

## 10. Deployment Strategy

### 10.1 Deployment Architecture

```
GitHub → GitHub Actions CI/CD → Docker Hub → EC2 Deployment
                                           ↓
                                    S3 (Frontend)
```

### 10.2 CI/CD Pipeline

**GitHub Actions Workflow**:

1. **Test Stage**
   - Run unit tests
   - Generate coverage report
   - Fail if coverage < 80%

2. **Build Backend**
   - Build Docker image
   - Push to Docker Hub
   - Tag with version and `latest`

3. **Build Frontend**
   - Build React app
   - Upload to S3
   - Invalidate CloudFront cache

4. **Deploy Backend**
   - SSH to EC2
   - Pull latest Docker image
   - Run `docker-compose up -d`
   - Health check

### 10.3 Deployment Scripts

**deploy.sh**:
- Pull latest images
- Stop existing containers
- Start new containers
- Wait for health check
- Rollback on failure

### 10.4 Environment Variables

**Backend** (`.env` file):
- `DB_PASSWORD`: Database password
- `JWT_SECRET`: JWT signing secret (min 256 bits)
- `CACHE_TYPE`: `caffeine` or `redis`
- `SPRING_PROFILES_ACTIVE`: `prod`

**Frontend** (`.env.production`):
- `VITE_API_BASE_URL`: Backend API URL

---

## 11. Monitoring & Logging

### 11.1 CloudWatch Integration

**Metrics Collected**:
- CPU utilization
- Memory utilization
- Disk usage
- Network traffic
- Application metrics (via Spring Boot Actuator)

**Logs Collected**:
- Application logs (JSON format)
- System logs
- Docker container logs

**Retention**: 30 days

### 11.2 Application Logging

**Configuration**:
- Structured JSON logging
- Log levels: INFO, WARN, ERROR
- Correlation IDs for request tracking
- Sensitive data masking

**Key Logs**:
- Seat state transitions
- Authentication attempts
- API errors
- Performance metrics

### 11.3 Alerts

**Alert Thresholds**:
- CPU > 80% for 5 minutes
- Memory > 85% for 5 minutes
- Disk > 90%
- API error rate > 5%
- API P95 latency > 1 second

**Notification**: Email/SNS

### 11.4 Health Checks

**Endpoints**:
- `/actuator/health`: Overall health status
- `/actuator/metrics`: Application metrics
- `/actuator/info`: Application info

**Monitoring**:
- Docker health checks every 30 seconds
- CloudWatch alarms on health check failures

---

## 12. Testing Strategy

### 12.1 Unit Testing

**Framework**: JUnit 5 + Mockito

**Coverage Target**: 80% minimum

**Focus Areas**:
- Service layer (business logic)
- Repository layer (database queries)
- Entity validation
- State machine transitions

**Test Structure**:
```
src/test/java/com/skyhigh/
├── service/
│   ├── SeatServiceTest.java
│   ├── CheckInServiceTest.java
│   └── WaitlistServiceTest.java
├── repository/
│   └── SeatRepositoryTest.java
└── security/
    └── JwtTokenProviderTest.java
```

### 12.2 Coverage Reporting

**Tool**: JaCoCo

**Configuration**:
- Minimum line coverage: 80%
- Minimum branch coverage: 70%
- Exclude: DTOs, entities, configuration classes

**Report**: `target/site/jacoco/index.html`

### 12.3 Running Tests

```bash
# Run all tests
mvn test

# Run with coverage
mvn clean test jacoco:report

# Run specific test
mvn test -Dtest=SeatServiceTest
```

### 12.4 Test Data

**TestContainers**: Use PostgreSQL test container for integration tests

**Sample Data**: Reuse Flyway migrations for test data

---

## 13. Migration Path

### 13.1 Phase 1: MVP (Current)

**Architecture**:
- Single EC2 with Docker (Backend + PostgreSQL)
- S3 + CloudFront (Frontend)
- In-memory caching (Caffeine)
- Spring @Scheduled tasks
- Simple JWT authentication

**Cost**: ~$45/month  
**Capacity**: 100-500 concurrent users  
**Uptime**: 95-99%

---

### 13.2 Phase 2: Separate Database

**Changes**:
- Migrate PostgreSQL to Amazon RDS
- Keep EC2 for backend only
- Automated backups enabled

**Migration Steps**:
1. Create RDS instance (db.t3.micro)
2. Export data from Docker PostgreSQL
3. Import to RDS
4. Update backend connection string
5. Test thoroughly
6. Switch traffic

**Cost**: ~$60/month (+$15)  
**Benefits**:
- Automated backups
- Point-in-time recovery
- Better reliability
- Easier scaling

---

### 13.3 Phase 3: Load Balancer

**Changes**:
- Add Application Load Balancer
- SSL/TLS termination at ALB
- Health checks

**Migration Steps**:
1. Create ALB
2. Configure target group (EC2 instance)
3. Update DNS to point to ALB
4. Test health checks

**Cost**: ~$76/month (+$16)  
**Benefits**:
- SSL/TLS termination
- Health checks
- Easy to add more instances
- Professional setup

---

### 13.4 Phase 4: High Availability

**Changes**:
- Auto Scaling Group (2+ EC2 instances)
- RDS Multi-AZ
- Redis ElastiCache
- Distributed caching

**Migration Steps**:
1. Create ElastiCache Redis cluster
2. Update cache configuration (`caffeine` → `redis`)
3. Create Auto Scaling Group
4. Enable RDS Multi-AZ
5. Test failover scenarios

**Cost**: ~$150-200/month  
**Capacity**: 1000+ concurrent users  
**Uptime**: 99.9%  
**Benefits**:
- High availability
- Auto-scaling
- Zero downtime deployments
- Distributed caching

---

## 14. Appendix

### 14.1 API Endpoint Summary

| Endpoint | Method | Description | Auth Required |
|----------|--------|-------------|---------------|
| `/api/v1/auth/login` | POST | User login | No |
| `/api/v1/flights/{id}/seat-map` | GET | Get seat map | Yes |
| `/api/v1/flights/{id}/seats/{seat}/reserve` | POST | Reserve seat | Yes |
| `/api/v1/check-ins/{id}/baggage` | POST | Add baggage | Yes |
| `/api/v1/check-ins/{id}/payment` | POST | Process payment | Yes |
| `/api/v1/check-ins/{id}/confirm` | POST | Confirm check-in | Yes |
| `/api/v1/check-ins/{id}/cancel` | POST | Cancel check-in | Yes |
| `/api/v1/flights/{id}/seats/{seat}/waitlist` | POST | Join waitlist | Yes |
| `/api/v1/waitlist/{id}` | DELETE | Leave waitlist | Yes |
| `/actuator/health` | GET | Health check | No |

### 14.2 Database Size Estimates

| Table | Rows (1 year) | Size Estimate |
|-------|---------------|---------------|
| `flights` | 10,000 | 5 MB |
| `passengers` | 100,000 | 50 MB |
| `seats` | 2,000,000 | 500 MB |
| `check_ins` | 500,000 | 200 MB |
| `baggage` | 300,000 | 100 MB |
| `waitlist` | 50,000 | 20 MB |
| `audit_logs` | 5,000,000 | 2 GB |
| **Total** | | **~3 GB** |

**Storage Recommendation**: 30 GB EBS volume (10x headroom)

### 14.3 Performance Benchmarks

| Operation | Target | Expected (MVP) |
|-----------|--------|----------------|
| Get Seat Map (cached) | < 300ms | 150-250ms |
| Get Seat Map (uncached) | < 1s | 400-800ms |
| Reserve Seat | < 500ms | 200-400ms |
| Confirm Check-In | < 1s | 500-900ms |
| Release Expired Seats | < 5s | 1-3s |
| Concurrent Reservations | 500+ | 300-500 |

### 14.4 Environment Configuration

**Development**:
- Profile: `dev`
- Database: Local PostgreSQL or Docker
- Cache: Caffeine
- Logging: DEBUG level

**Production**:
- Profile: `prod`
- Database: PostgreSQL in Docker (Phase 1) or RDS (Phase 2+)
- Cache: Caffeine (configurable to Redis)
- Logging: INFO level

### 14.5 Glossary

| Term | Definition |
|------|------------|
| **MVP** | Minimum Viable Product |
| **ACID** | Atomicity, Consistency, Isolation, Durability |
| **JWT** | JSON Web Token |
| **ALB** | Application Load Balancer |
| **RDS** | Relational Database Service |
| **EBS** | Elastic Block Store |
| **CDN** | Content Delivery Network |
| **TTL** | Time To Live |
| **Optimistic Locking** | Concurrency control using version numbers |
| **P95 Latency** | 95th percentile response time |

### 14.6 References

1. **Spring Boot Documentation**: https://spring.io/projects/spring-boot
2. **PostgreSQL Documentation**: https://www.postgresql.org/docs/
3. **Docker Compose**: https://docs.docker.com/compose/
4. **AWS EC2**: https://docs.aws.amazon.com/ec2/
5. **Terraform AWS Provider**: https://registry.terraform.io/providers/hashicorp/aws/
6. **React Documentation**: https://react.dev/
7. **Material-UI**: https://mui.com/
8. **AviationStack API Documentation**: https://aviationstack.com/documentation
9. **Resilience4j Documentation**: https://resilience4j.readme.io/docs

### 14.7 Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-02-27 | SkyHigh Engineering | Initial MVP TRD |
| 1.1 | 2026-02-27 | SkyHigh Engineering | Added AviationStack API integration (Section 9.2) |

---

**Document Status**: Draft  
**Next Review Date**: 2026-03-15  
**Contact**: skyhigh-core-team@skyhigh.com

---

*End of Technical Requirements Document (MVP)*
