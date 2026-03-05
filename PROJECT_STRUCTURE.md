## Project Structure

This document explains how the SkyHigh Core repository is organized, what each folder is responsible for, and how the main backend modules fit together.

### 1. Top-Level Layout

```text
Sky-High/
├── backend/           # Spring Boot backend (REST API, business logic, DB)
├── frontend/          # React + TypeScript SPA (passenger web UI)
├── deployment/        # Infra and deployment automation (AWS, scripts)
├── tasks/             # Task breakdown, optimization tasks, and guides
├── backup/            # Backup copies of older docs / milestones
├── .github/           # CI/CD workflows (GitHub Actions)
├── docker-compose.yml # Local dev stack (backend + DB + Redis + frontend)
├── PRD.md             # Product Requirements Document
├── TRD.md             # Technical Requirements Document
├── ARCHITECTURE.md    # System and data architecture
├── PROJECT_STATUS.md  # High-level delivery status and milestones
└── README.md          # Getting started, setup, and deployment
```

- **backend/**: Complete Spring Boot application that exposes the REST API and implements all business workflows, integrations, and persistence logic.
- **frontend/**: Single-page application that talks to the backend APIs and provides the passenger-facing experience (check-in, seat selection, baggage, waitlist).
- **deployment/**: Infrastructure definitions (e.g., CloudFormation) and deployment scripts for provisioning AWS resources and rolling out new versions.
- **tasks/**: Planning artifacts, implementation tasks, and performance/Redis optimization guides that describe how the system evolved.
- **backup/**: Archived versions of documentation or implementation notes kept for reference.
- **.github/**: GitHub Actions workflows for CI/CD (build, test, and deployment automation).
- **docker-compose.yml**: One-command local environment that spins up backend, PostgreSQL, Redis, and (optionally) the frontend.
- **PRD.md / TRD.md / ARCHITECTURE.md / PROJECT_STATUS.md**: Core documentation capturing product/technical requirements, architecture decisions, and delivery status.

---

### 2. Backend Structure (`backend/`)

The backend is a standard Maven-based Spring Boot project organized by feature responsibilities.

```text
backend/
├── src/
│   ├── main/
│   │   ├── java/com/skyhigh/
│   │   │   ├── controller/
│   │   │   ├── service/
│   │   │   ├── repository/
│   │   │   ├── entity/
│   │   │   ├── dto/
│   │   │   ├── config/
│   │   │   ├── exception/
│   │   │   ├── security/
│   │   │   ├── scheduler/
│   │   │   ├── event/
│   │   │   └── health/
│   │   └── resources/
│   │       ├── application.yml
│   │       └── db/migration/
│   └── test/
├── pom.xml
└── Dockerfile
```

- **`com.skyhigh.SkyHighCoreApplication`**: Spring Boot entry point; boots the web application, loads configuration, and registers beans.

#### 2.1 Controllers (`controller/`)

HTTP layer for all external API endpoints.

- **`AuthController`**: Login endpoint, issues JWTs, and exposes basic auth-related operations.
- **`FlightController`**: Read-only APIs for listing flights and retrieving flight details.
- **`SeatController`**: Seat map retrieval, seat reservation/confirmation/cancellation operations.
- **`CheckInController`**: Endpoints for starting and completing check-in, attaching baggage, and viewing check-in summaries.
- **`WaitlistController`**: Operations to join or leave the waitlist and check current waitlist position.

Controllers are intentionally thin: they validate input, delegate to services, and map service responses to DTOs.

#### 2.2 Services (`service/`)

Business logic and workflows live here. Each service encapsulates a cohesive domain concern:

- **`AuthenticationService`**: Verifies user credentials, integrates with `UserService` and JWT provider, and returns login responses.
- **`UserService`**: Looks up and manages application users used for authentication.
- **`FlightService`**: Encapsulates flight lookup, status checks, and any flight-related business rules.
- **`SeatService`**: Implements the full seat life cycle (AVAILABLE → HELD → CONFIRMED → CANCELLED), including validation, optimistic locking, and integration with distributed seat lock and cache invalidation.
- **`CheckInService`**: Orchestrates check-in flows, including linking reservations, seats, baggage, and updating check-in status.
- **`BaggageService`**: Handles baggage creation and updates linked to check-ins, including weight and type.
- **`WaitlistService`**: Manages waitlist entries, position tracking, and promotion when seats become available.
- **`PaymentService`**: Encapsulates interactions with the payment provider (mock in this project) and updates payment status.
- **`NotificationService`**: Sends notifications (e.g., confirmation emails or messages) when key events occur.
- **`AuditLogService`**: Persists structured audit entries when important state changes occur (seats, check-ins, waitlist, etc.).
- **`SeatMapCacheService` / `RedisSeatMapCacheService` / `NoOpSeatMapCacheService`**: Abstraction and implementations for seat map caching in Redis (or a no-op fallback when caching is disabled).
- **`DistributedSeatLockService` / `RedisDistributedSeatLockService` / `NoOpDistributedSeatLockService`**: Abstraction and implementations for distributed seat locking using Redis (or a no-op implementation in single-instance mode).
- **`WeightService`**: Integration boundary for an external weight service (used in baggage/check-in flows).

Service methods are typically transactional and enforce domain invariants and state transitions.

#### 2.3 Repositories (`repository/`)

Spring Data JPA repositories for persistence of all core entities:

- **`FlightRepository`**, **`SeatRepository`**, **`ReservationRepository`**, **`PassengerRepository`**, **`CheckInRepository`**, **`BaggageRepository`**, **`WaitlistRepository`**, **`AuditLogRepository`**.

Repositories hide raw SQL and provide type-safe query methods and custom JPQL queries where needed (e.g., to find available seats or release expired holds).

#### 2.4 Entities & Enums (`entity/`, `enums/`)

JPA-mapped domain entities representing the database schema:

- **Entities**: `Flight`, `Passenger`, `Reservation`, `Seat`, `CheckIn`, `Baggage`, `Waitlist`, `AuditLog`.
- **Enums**: `SeatState`, `SeatType`, `CheckInStatus`, `FlightStatus`, `WaitlistStatus`, `BaggageType`, `PaymentStatus`.
- **Attribute Converters**: Convert enum and type-safe fields to DB-friendly representations (e.g., `SeatStateConverter`, `BaggageTypeConverter`).

Entities follow the patterns described in `database-patterns.mdc`, including optimistic locking fields, audit timestamps, and proper relationships between tables.

#### 2.5 DTOs (`dto/`)

Request and response models used at the API boundary:

- Auth: `LoginRequest`, `LoginResponse`.
- Flights and seats: `FlightDTO`, `SeatDTO`, `SeatMapResponseDTO`, `SeatReservationRequestDTO`, `SeatReservationResponseDTO`.
- Check-in: `CheckInRequestDTO`, `CheckInResponseDTO`, `PassengerCheckInSummaryDTO`.
- Baggage: `BaggageDetailsDTO`, `BaggageResponseDTO`.
- Waitlist: `WaitlistJoinRequestDTO`, `WaitlistResponseDTO`, `WaitlistPositionDTO`.
- Payments: `PaymentRequestDTO`, `PaymentResponseDTO`.
- Errors: `ErrorResponse`.

DTOs decouple external contracts from internal entity models and are also used in tests to assert API behavior.

#### 2.6 Configuration (`config/`)

Spring configuration classes and infrastructure wiring:

- **`SecurityConfig`**: Spring Security filter chain, endpoint access rules, and authentication configuration.
- **`OpenApiConfig`**: OpenAPI/Swagger documentation setup.
- **`JacksonConfig`**: JSON serialization/deserialization customizations.
- **`AsyncConfig`**: Asynchronous execution pool configuration.
- **`SchedulerConfig`**: Schedules and thread pools for background jobs.
- **`CacheConfig`** / **`RedisSeatMapCacheConfig`** / **`RedisDistributedSeatLockConfig`**: Wiring for caches and Redis-based seat map cache and distributed seat lock.

These classes keep cross-cutting technical concerns separate from business logic.

#### 2.7 Security (`security/`)

Security-related helpers and components:

- **`JwtTokenProvider`**: Creates, parses, and validates JWT tokens.
- **`JwtAuthenticationFilter`**: Extracts and validates JWTs from incoming requests.
- **`JwtAuthenticationEntryPoint`**: Handles unauthorized access responses.
- **`User`**: Security principal/user details representation used by Spring Security.

#### 2.8 Scheduling & Events (`scheduler/`, `event/`, `health/`)

- **`SeatExpirationScheduler`** (`scheduler/`): Periodic job that releases expired seat holds based on `held_until` and state.
- **`SeatMapCacheInvalidationEvent`**, **`SeatMapCacheInvalidationEventListener`**, **`SeatReleasedEvent`**, **`WaitlistEventListener`** (`event/`): Domain events and listeners for reacting to seat/ waitlist changes and invalidating caches.
- **`SchedulerHealthIndicator`** (`health/`): Custom health indicator to surface scheduler health via Actuator.

#### 2.9 Exceptions (`exception/`)

Domain-specific exceptions and global error handling:

- **Domain exceptions**: e.g., `SeatNotFoundException`, `SeatConflictException`, `SeatLockConflictException`, `InvalidStateTransitionException`, `CheckInNotFoundException`, `BaggageNotFoundException`, `WaitlistNotFoundException`, `PaymentFailedException`, `AuthenticationFailedException`, `UnauthorizedException`, `NotificationFailedException`.
- **`GlobalExceptionHandler`**: Centralized `@ControllerAdvice` that translates exceptions into consistent `ErrorResponse` payloads and HTTP status codes.

This layer keeps error semantics explicit and ensures APIs fail in a predictable, well-documented way.

#### 2.10 Resources & Migrations (`resources/`)

- **`application.yml`**: Spring Boot configuration for DB, logging, security, Redis, and feature toggles (e.g., enabling Redis-based caching and locks).
- **`db/migration/`**: Flyway migration scripts (`V1__...sql` to `V11__...sql`) defining the entire database schema and inserting seed and test data (flights, passengers, seats, users).

---

### 3. Frontend Structure (`frontend/`)

The frontend is a React + TypeScript SPA built with Vite.

```text
frontend/
├── src/
│   ├── components/   # Reusable UI components (forms, tables, seat map, etc.)
│   ├── pages/        # Page-level views (Login, Check-in, Seat Selection, Summary)
│   ├── services/     # API clients for backend endpoints
│   ├── hooks/        # Reusable stateful logic (auth, polling, forms)
│   ├── contexts/     # React context providers (auth, app-level state)
│   ├── types/        # Shared TypeScript types and interfaces
│   ├── utils/        # Generic utilities (formatting, validation, helpers)
│   └── assets/       # Static assets (icons, logos, styles)
├── package.json
├── Dockerfile
└── nginx.conf
```

- **Components & pages**: Implement the passenger experience (log in, view flights, choose seats, manage baggage, join waitlists, view boarding pass/check-in summary).
- **Services**: Encapsulate HTTP calls to the backend (auth, flights, seats, check-in, baggage, waitlist) and handle token attachment and error transformation.
- **Contexts & hooks**: Provide application-wide state (e.g., authenticated user, selected flight, seat selection) and reuse complex view logic.

---

### 4. Deployment & Automation (`deployment/`, `.github/`)

```text
deployment/
├── aws/
│   ├── cloudformation-template.yml # AWS infra (VPC, EC2, security groups, S3, CloudFront, etc.)
│   └── deploy-infrastructure.sh    # Helper script to deploy infra stack
└── scripts/
    ├── setup-ec2.sh               # Bootstraps EC2 (Docker, directories, environment)
    ├── deploy.sh                  # Builds/pulls images and restarts services
    └── rollback.sh                # Rollback strategy for failed deployments

.github/
└── workflows/                     # CI workflows (build, test, and possibly deploy)
```

- **deployment/aws**: Infrastructure-as-code for AWS; ensures consistent environments for staging/production.
- **deployment/scripts**: Operational scripts to configure servers and perform deployments/rollbacks.
- **.github/workflows**: CI/CD pipelines that run tests, enforce quality gates, and automate build/deploy steps.

---

### 5. Supporting Documentation & Tasks (`tasks/`, `backup/`)

- **`tasks/`**: Contains task breakdowns, implementation checklists, and performance optimization plans (including Redis-based seat map cache, rate limiting, and distributed seat lock).
  - `tasks/optimization-tasks/`: Deep-dive guides on how to enable and tune Redis-backed optimizations and fallbacks.
- **`backup/`**: Historical snapshots of documentation and intermediate implementation notes; useful for tracing how the design evolved over time but not used by the running system.

These folders are for humans (engineers, reviewers, stakeholders) and do not affect application runtime behavior.

