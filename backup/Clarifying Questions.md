# Clarifying Questions & Recommendations

## 1. Database Choice

**Question:**
For the single EC2 setup, should I use PostgreSQL (as suggested in PRD) or would you prefer MySQL? Both can run on EC2, but PostgreSQL has better support for JSON columns and concurrency control.
Should the database run on the same EC2 instance or a separate RDS instance? (RDS would be more reliable but adds complexity)

**✅ Recommendation: PostgreSQL on Amazon RDS**

**Rationale:**
- **PostgreSQL**: Better support for JSONB (audit logs), robust MVCC for concurrency, excellent optimistic locking support
- **RDS (not EC2)**: 
  - Automated backups and point-in-time recovery
  - Automated patching and maintenance
  - Multi-AZ deployment option for high availability
  - Minimal operational overhead
  - Easy to scale (vertical scaling with instance type change)
  - Only slightly more complex than EC2-hosted, but much more reliable

**Configuration:**
- Instance: `db.t3.micro` (1 vCPU, 1 GB RAM) - ~$15/month
- Storage: 20 GB SSD with auto-scaling
- Automated backups: 7-day retention
- Single-AZ for MVP (Multi-AZ for production)

---

## 2. Redis/Caching Strategy

**Question:**
The PRD mentions Redis for caching and rate limiting. For a simple setup:
Should I include Redis on the same EC2 instance, or
Skip Redis entirely and use in-memory caching (Spring Boot's @Cacheable with Caffeine)?
Note: Skipping Redis simplifies deployment but reduces performance for high concurrency scenarios.

**✅ Recommendation: In-Memory Caching (Caffeine) for MVP**

**Rationale:**
- **Zero infrastructure overhead**: No Redis to deploy, manage, or monitor
- **Sufficient for MVP**: Can achieve P95 < 1 second with single EC2 instance
- **Easy to implement**: Spring Boot `@Cacheable` annotations
- **Cost-effective**: $0 additional cost
- **Future-proof**: Easy to migrate to Redis later with minimal code changes

**Caching Strategy:**
```yaml
Seat Map Structure: 5 minutes TTL
Seat Availability: 10 seconds TTL
Cache Invalidation: Immediate on state change
Technology: Spring Boot + Caffeine
```

**When to upgrade to Redis:**
- Scaling to multiple EC2 instances (shared cache needed)
- Traffic exceeds 1000 concurrent users
- Need distributed rate limiting

---

## 3. Background Jobs & Timers

**Question:**
The PRD requires a 120-second seat hold timer. For simple deployment:
Option A: Use Spring's @Scheduled tasks (runs in-process, simpler)
Option B: Use a separate job queue like RabbitMQ (more robust but adds complexity)
Which approach do you prefer?

**✅ Recommendation: Spring @Scheduled Tasks (Option A)**

**Rationale:**
- **Simple**: No external dependencies (RabbitMQ, SQS)
- **Reliable**: Database-backed timer state (survives restarts)
- **Sufficient for MVP**: Can handle 1000+ concurrent timers
- **Easy to implement**: Single annotation `@Scheduled(fixedDelay = 5000)`

**Implementation:**
```java
@Scheduled(fixedDelay = 5000) // Run every 5 seconds
public void releaseExpiredSeats() {
    // Query: held_until < NOW() AND state = 'HELD'
    // Update: state = 'AVAILABLE'
}
```

**When to upgrade to Message Queue:**
- Need guaranteed delivery semantics
- Complex workflow orchestration
- Distributed processing across multiple workers

---

## 4. External Services (Weight, Payment, Notification)

**Question:**
Should I:
Option A: Mock these services initially with stub implementations?
Option B: Design integration points but leave actual integration for later?
Option C: Use AWS services (SES for email, SNS for SMS)?

**✅ Recommendation: Hybrid Approach**

**Weight Service**: Mock/Stub implementation (Option A)
- Simple in-memory validation logic
- Returns success/failure based on weight threshold

**Payment Service**: Mock/Stub implementation (Option A)
- Simulates payment processing with configurable success/failure
- Logs transaction for audit

**Notification Service**: AWS SES for email (Option C)
- Real email delivery for user notifications
- Simple AWS SDK integration
- Cost: $0.10 per 1000 emails (very cheap)
- Easy to add SMS (SNS) later if needed

**Rationale:**
- **Mock Weight/Payment**: Focus on core check-in logic, not external integrations
- **Real Email (SES)**: Provides tangible user experience, minimal complexity
- **Clear interfaces**: Easy to replace mocks with real services later

---

## 5. Frontend Deployment on S3

**Question:**
Should the React frontend be:
Static hosting on S3 + CloudFront (recommended, simpler, cheaper), or
Served from the same EC2 instance (simpler infrastructure but less scalable)?

**✅ Recommendation: S3 + CloudFront (Static Hosting)**

**Rationale:**
- **Industry standard**: Best practice for React SPAs
- **Cost-effective**: S3 storage ~$0.023/GB, CloudFront ~$0.085/GB transfer
- **Performance**: Global CDN with edge caching
- **Scalability**: Handles unlimited traffic
- **Simplicity**: No server management for frontend
- **Separation of concerns**: Frontend and backend independently deployable

**Configuration:**
- S3 bucket with static website hosting
- CloudFront distribution for HTTPS and CDN
- React app calls backend API via CORS

**Cost estimate**: ~$5-10/month for moderate traffic

---

## 6. Authentication

**Question:**
The PRD mentions OAuth 2.0 / JWT. For MVP:
Option A: Simple JWT-based auth with hardcoded users/API keys?
Option B: Integrate with AWS Cognito (adds complexity but production-ready)?
Option C: Skip auth initially and add later?

**✅ Recommendation: Simple JWT Authentication (Option A)**

**Rationale:**
- **Sufficient for MVP**: Demonstrates auth without over-engineering
- **Easy to implement**: Spring Security + JWT library
- **Testable**: Hardcoded users for demo/testing
- **Upgradeable**: Can migrate to Cognito/OAuth later

**Implementation:**
```java
// Hardcoded users for MVP
passengers:
  - id: P123456, email: john@example.com, password: demo123
  - id: P789012, email: jane@example.com, password: demo456

// JWT token with 1-hour expiration
// Endpoints: POST /auth/login, POST /auth/refresh
```

**When to upgrade to Cognito:**
- Production deployment
- Need user registration/password reset
- Multi-factor authentication required

---

## 7. Monitoring & Logging

**Question:**
For simple setup:
Option A: Use CloudWatch for logs and basic metrics (AWS native, simple)?
Option B: Include Spring Boot Actuator + Prometheus/Grafana (more detailed but requires setup)?
Option C: Minimal logging to files initially?

**✅ Recommendation: CloudWatch + Spring Boot Actuator (Hybrid)**

**Rationale:**
- **CloudWatch Logs**: Automatic log aggregation, zero setup
- **CloudWatch Metrics**: Basic CPU, memory, disk, network metrics
- **Spring Boot Actuator**: Health checks, metrics endpoints (for ALB health checks)
- **No Prometheus/Grafana**: Avoid additional infrastructure for MVP

**Configuration:**
```yaml
# Spring Boot Actuator
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,info
  metrics:
    export:
      cloudwatch:
        enabled: true
```

**Logging:**
- Structured JSON logging
- Log levels: INFO, WARN, ERROR
- CloudWatch log retention: 30 days

**When to upgrade:**
- Need custom dashboards (add Grafana)
- Advanced alerting rules (add Prometheus)

---

## 8. Load Balancer

**Question:**
The PRD shows an ALB (Application Load Balancer). For single EC2:
Option A: Skip load balancer initially (direct EC2 access)?
Option B: Include ALB for future scalability (adds cost ~$16/month)?

**✅ Recommendation: Include ALB (Option B)**

**Rationale:**
- **SSL/TLS termination**: Easy HTTPS setup with ACM certificate
- **Health checks**: Automatic EC2 health monitoring
- **Future-proof**: Easy to add more EC2 instances later (zero code changes)
- **Professional setup**: Industry standard architecture
- **Cost**: ~$16/month is reasonable for production-ready setup

**Configuration:**
- ALB with single target (EC2 instance)
- Health check: `GET /actuator/health` every 30 seconds
- SSL certificate from AWS Certificate Manager (free)
- Security group: Allow 443 (HTTPS) only

**Alternative (if cost is critical):**
- Skip ALB, use Elastic IP on EC2
- Manual SSL certificate management
- Save ~$16/month but lose health checks and easy scaling

---

## 9. Database Migrations

**Question:**
Should I include:
Flyway or Liquibase for database schema versioning, or
Simple SQL scripts to run manually?

**✅ Recommendation: Flyway**

**Rationale:**
- **Industry standard**: Widely used, well-documented
- **Version control**: Database schema in Git
- **Automated**: Runs migrations on application startup
- **Rollback support**: Can revert changes if needed
- **Minimal overhead**: Simple Maven/Gradle dependency
- **CI/CD friendly**: Integrates with deployment pipelines

**Configuration:**
```yaml
# application.yml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true
```

**Migration files:**
```
src/main/resources/db/migration/
  V1__create_flights_table.sql
  V2__create_seats_table.sql
  V3__create_passengers_table.sql
  ...
```

**Why not Liquibase?**
- Flyway is simpler for SQL-first approach
- Liquibase better for XML/YAML-based migrations (not needed for MVP)

---

## 10. Testing Strategy

**Question:**
What level of testing should the TRD specify:
Unit tests only (80% coverage)?
Integration tests for API endpoints?
Load testing scripts for concurrency validation?

**✅ Recommendation: Comprehensive Testing (All Three)**

**Rationale:**
- **Unit Tests**: Essential for business logic, service layer
- **Integration Tests**: Critical for API endpoints, database interactions
- **Load Tests**: Required to validate concurrency guarantees (seat conflicts)

**Testing Strategy:**

### Unit Tests (80% coverage)
```
Technology: JUnit 5, Mockito
Focus: Service layer, business logic, state machine
Target: 80%+ code coverage
```

### Integration Tests
```
Technology: Spring Boot Test, TestContainers (PostgreSQL)
Focus: API endpoints, database transactions, concurrency
Tests:
  - Seat reservation with optimistic locking
  - Timer expiration logic
  - Waitlist assignment
  - Payment flow
```

### Load Tests
```
Technology: JMeter or Gatling
Focus: Concurrency validation
Scenarios:
  - 500 concurrent users reserving same seat (only 1 succeeds)
  - 1000 requests/sec for seat map access (P95 < 1 second)
  - 100 concurrent check-ins with baggage payment
```

**CI/CD Integration:**
- Unit tests: Run on every commit
- Integration tests: Run on every PR
- Load tests: Run before production deployment

---

## Summary of Recommendations

| Question | Recommendation | Rationale |
|----------|---------------|-----------|
| **1. Database** | PostgreSQL on RDS | Reliable, managed, better concurrency support |
| **2. Caching** | In-memory (Caffeine) | Simple, sufficient for MVP, easy to upgrade |
| **3. Timers** | Spring @Scheduled | Simple, no external dependencies |
| **4. External Services** | Mock Weight/Payment, Real Email (SES) | Focus on core logic, real user experience |
| **5. Frontend** | S3 + CloudFront | Industry standard, scalable, cost-effective |
| **6. Authentication** | Simple JWT | Sufficient for MVP, upgradeable |
| **7. Monitoring** | CloudWatch + Actuator | AWS native, minimal setup |
| **8. Load Balancer** | Include ALB | Future-proof, professional setup |
| **9. Migrations** | Flyway | Industry standard, version control |
| **10. Testing** | Unit + Integration + Load | Comprehensive validation |

---

## Estimated Monthly AWS Cost (MVP)

| Service | Configuration | Estimated Cost |
|---------|--------------|----------------|
| EC2 (t3.medium) | 2 vCPU, 4 GB RAM | ~$30 |
| RDS (db.t3.micro) | 1 vCPU, 1 GB RAM | ~$15 |
| ALB | Application Load Balancer | ~$16 |
| S3 + CloudFront | Static hosting | ~$5-10 |
| CloudWatch | Logs and metrics | ~$5 |
| **Total** | | **~$71-76/month** |

**Cost optimization options:**
- Remove ALB: Save $16/month (use Elastic IP)
- Use t3.small EC2: Save $15/month (1 vCPU, 2 GB RAM)
- **Optimized total**: ~$40-45/month