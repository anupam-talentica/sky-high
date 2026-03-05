## Chat History – Design Journey

This document summarizes how AI assistance was used while designing and implementing **SkyHigh Core**. It focuses on key decision points, alternatives considered, and how AI helped evaluate trade-offs.

---

## 1. Framing the Problem & Requirements

- **Clarifying the core problem**
  - You used the assistant to restate the business scenario (high-volume digital check-in, conflict-free seat assignment, 120-second holds, waitlist, baggage/payment, abuse detection) into a structured **PRD** with explicit functional and non-functional requirements.
  - The AI helped translate narrative requirements into concrete items like **FR-001 – FR-009**, ensuring that every business rule from the problem statement mapped to a testable requirement.
- **Evaluating scope for MVP vs future**
  - The assistant proposed a clear separation between **MVP capabilities** (single EC2 instance, in-memory or simple Redis usage, basic JWT auth, simple rate limiting) and **future enhancements** (Redis-based distributed rate limiting, multi-AZ RDS, message queues, advanced observability).
  - This helped keep the first version deliverable on a single EC2 + Docker setup, while still documenting a path to scale.

---

## 2. Core Architecture & Infrastructure Choices

- **Backend–frontend–data layout**
  - AI helped converge on a **three-tier architecture**: React SPA frontend, Spring Boot backend, PostgreSQL as source of truth, and Redis as a performance and rate-limiting layer.
  - It recommended a pragmatic **“single EC2 + Docker Compose”** MVP deployment, with a documented evolution path to ALB + auto-scaling + RDS + ElastiCache as described in `ARCHITECTURE.md`.
- **PostgreSQL as system of record**
  - Alternatives: NoSQL store, in-memory only for demos, or a lighter relational DB.
  - AI argued for PostgreSQL due to strong transactional guarantees, familiar tooling, and alignment with **optimistic locking** and **strict seat state machine** requirements.
  - This influenced the ERD and Flyway migrations (`flights`, `seats`, `check_ins`, `waitlist`, `baggage`, `audit_logs`).
- **Authentication model**
  - Alternatives: Session-based auth, API keys only, OAuth2/OIDC for passengers.
  - AI recommended **stateless JWT** for the SPA + API model, minimizing server session state and matching MVP constraints, while noting that a full IdP/Cognito-style setup can be added later.

---

## 3. Seat State Machine, Concurrency & Consistency

- **Seat lifecycle enforcement**
  - Starting from the problem statement’s states, the assistant helped refine the **seat state machine** (`AVAILABLE → HELD → CONFIRMED → CANCELLED`) and connected it to specific JPA entities and DB constraints.
  - It emphasized validating state transitions in code and at the DB level, returning `409 Conflict` for invalid transitions and logging all changes into `audit_logs`.
- **Optimistic vs pessimistic locking**
  - Alternatives considered:
    - **Pessimistic locking** (`SELECT ... FOR UPDATE`) to block concurrent writers.
    - **Optimistic locking** with `@Version` and conflict handling.
    - Purely application-level locks without DB enforcement.
  - AI recommended **optimistic locking** on `seats.version`, plus unique constraints (`flight_id`, `seat_number`), because:
    - It satisfies “zero duplicate seat assignments” even under high concurrency.
    - It scales better under read-heavy workloads than broad pessimistic locks.
    - It keeps DB-level source-of-truth semantics, even if multiple app nodes are added later.
  - This drove the `Seat` entity design (version field, state enum) and conflict handling patterns in the service layer.
- **Time-bound seat holds & scheduler design**
  - The assistant helped translate the “exactly 120 seconds” requirement into a concrete pattern:
    - Store `held_until` timestamps in the `seats` table.
    - Use a **Spring `@Scheduled` job** to release `HELD` seats whose `held_until < now()`, running frequently enough to meet the ±2 second tolerance.
    - Wrap releases in the same transactional + locking semantics as seat reservation.
  - It also documented how this interacts with waitlist promotion and cache invalidation.

---

## 4. Performance, Caching & Redis Design

- **Choosing Redis vs DB-only vs in-memory cache**
  - Alternatives:
    - **DB-only** implementation with good indexes.
    - **In-memory cache** within a single JVM (Caffeine).
    - **Redis** as shared cache/rate-limit store between multiple nodes.
  - AI proposed a phased approach:
    - MVP can work with **in-memory Caffeine** and a single node.
    - For the performance requirements (P95 < 1s seat map, hundreds of concurrent users), introduce **Redis seat-map cache** as a core optimization, with Caffeine as an optional fallback.
  - This reasoning directly informed the **Performance Optimization** document and the Redis-related tasks under `tasks/optimization-tasks`.
- **Seat map caching strategy**
  - The assistant helped define:
    - Cache keys and structure (per-flight seat maps, short TTL).
    - Invalidation rules (on any seat state change, cancellation, or expiry).
    - Trade-offs between **strict freshness** and **read performance** (short TTL + invalidation provides near real-time behavior without hammering the DB).
  - It also captured P50/P95/P99 targets and how caching plus DB indexing can realistically hit them.
- **Distributed seat lock vs DB-only control**
  - Alternatives:
    - Only DB-level optimistic locking and unique constraints.
    - Optional **Redis-based distributed locks per seat** when horizontally scaling the backend.
  - AI recommended:
    - For MVP: rely primarily on **DB-level optimistic locking** for correctness.
    - Introduce a Redis lock only if contention/throughput demands it in a multi-node deployment, keeping the lock as an optimization layer, not the source of truth.
  - This decision appears in `ARCHITECTURE.md` as “optional Redis-based distributed seat lock” and in the Redis configuration classes.

---

## 5. Abuse Detection, Rate Limiting & Audit

- **Rate limiting model**
  - Alternatives:
    - Rate limiting purely at the edge (e.g. CloudFront/WAF rules).
    - Application-only, in-memory counters per instance.
    - Redis-backed distributed counters.
  - AI first anchored on **simple, in-process rate limiting** (Bucket4j style) for MVP and then extended the design to **Redis-based counters** to support:
    - Per-source (IP or user) request counting over sliding windows.
    - Higher accuracy and consistency when more than one backend instance is running.
- **Abuse/bot detection for seat map scraping**
  - The assistant helped formalize the “50 seat maps in 2 seconds” abuse scenario into:
    - Concrete Redis key design (e.g. counters per source).
    - Block TTLs and HTTP 429 responses with clear error messages.
    - Integration with the seat map endpoints so every request passes through the abuse detection filter.
- **Audit log storage choices**
  - Alternatives:
    - Storing all audit events in PostgreSQL only.
    - Offloading audit and abuse logs to **Elasticsearch** (or similar) for better querying.
  - AI advised starting with a **normalized `audit_logs` table** (low operational overhead, good enough for MVP) but documented how and when a **search store** like Elasticsearch becomes valuable (high volume, complex queries, dashboarding).
  - This split is captured in `performance-optimization.md` with Redis handling fast-path counters and either DB or Elasticsearch holding searchable audit history.

---

## 6. API Design & DTO Layer

- **Converging on REST endpoints**
  - The assistant helped turn high-level flows (seat reservation, baggage, payment, waitlist, flight status) into a consistent **REST API surface** documented in `PRD.md` and OpenAPI-style sections:
    - `GET /flights/{flightId}/seat-map`
    - `POST /flights/{flightId}/seats/{seatNumber}/reserve`
    - `POST /check-ins/{checkInId}/baggage`
    - `POST /check-ins/{checkInId}/payment`
    - `POST /check-ins/{checkInId}/confirm`
    - Waitlist and flight status endpoints.
  - It enforced consistent **error response shapes** and HTTP status codes (`409` for conflicts, `423` for locks, `429` for rate limits), improving UX and debuggability.
- **DTOs vs entities**
  - AI recommended a clear separation between **DTOs** (for API contracts) and **JPA entities** (for persistence), to avoid leaking internal structure and to keep future schema changes safer.
  - This guidance led to the DTO package structure in the backend, mirroring the API requirements from the PRD.

---

## 7. Documentation, Testing & Quality Focus

- **Documentation structure**
  - The assistant helped define a documentation plan covering:
    - `PRD.md` (requirements and data models),
    - `ARCHITECTURE.md` (system and data architecture),
    - `PROJECT_STRUCTURE.md`, `BOARDING_PASS_ENHANCEMENT.md`, and workflow/performance documents,
    - `CHAT_HISTORY.md` (this file) to capture the AI-assisted journey.
  - It emphasized consistent naming, diagrams (Mermaid for ERD/flow/infra), and keeping docs aligned with actual code (e.g., migrations and configuration classes).
- **Testing & quality strategy**
  - AI highlighted the importance of:
    - JUnit + Mockito unit tests around seat state transitions, hold expiry, and concurrency conflict handling.
    - Integration tests for schedulers, Redis integration, JWT security filters, and rate limiting.
    - Load-testing scenarios aligned with PRD metrics (P95 latency, concurrent users, seat conflict rate).
  - This guided the creation of backend test classes (e.g., scheduler tests, JWT tests) and the general expectation of **>80% coverage** for critical modules.

---

## 8. How AI Shaped the Final Design

Across the project, AI assistance consistently:

- **Mapped vague business needs to concrete, testable requirements**, especially around concurrency, timing guarantees, and performance SLAs.
- **Compared architectural alternatives** (locking strategies, cache designs, data stores, infra levels of complexity) and documented trade-offs so you could make informed decisions.
- **Maintained end-to-end consistency** between problem statement, PRD, architecture, migrations, code structure, and optimization tasks.
- **Surfaced future-ready options** (multi-AZ, Redis locks, Elasticsearch audit) while keeping the MVP simple enough to implement and operate on a single EC2 instance.

This collaborative loop between you and the AI assistant produced a design that is both realistic to ship as an MVP and structured to grow into a production-grade digital check-in platform.

