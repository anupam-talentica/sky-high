# Task 013: Notification Service via Redis Queue

**Feature**: FR-004 / FR-005 – Check-in & Waitlist Notifications  
**Priority**: P2 (Medium)  
**Estimated Effort**: 6–10 hours  
**Status**: Not Started  
**Owner**: Backend Team  
**Created**: March 5, 2026

---

## 1. Overview

### 1.1 Objective
Implement a notification service abstraction that **publishes notification events to a Redis-backed queue** instead of calling an email/SMS provider directly. A separate worker (in the same codebase or external) can consume this queue and perform the actual delivery.

### 1.2 Business Value
- Decouples core check-in / waitlist flows from notification delivery latency and failures.  
- Enables future multi-channel notifications (email, SMS, push) without changing domain flows.  
- Allows reliable retry, backoff, and dead-letter handling to be added later around the Redis queue.

### 1.3 Technical Scope
- Introduce a `NotificationService` interface with methods used by check-in, cancellation, and waitlist flows.  
- Implement a Redis-backed publisher that writes notification payloads to a queue structure (e.g., Redis List or Stream).  
- Replace all direct notification calls with queue-based publishing.  
- Add configuration, DTOs, and basic observability around the queue.

---

## 2. Prerequisites

- Redis service available (already defined in `docker-compose.yml` as `redis` with `REDIS_HOST` / `REDIS_PORT` env vars).  
- Core flows implemented for:
  - Seat confirmation / check-in completion  
  - Check-in cancellation  
  - Waitlist assignment / promotion  
- Basic notification requirements clarified from PRD (who gets notified, when, and with what content).

---

## 3. Implementation Tasks

### 3.1 Notification Model & Interface
- [ ] Define `NotificationType` enum (e.g., `WAITLIST_ASSIGNED`, `CHECK_IN_CONFIRMED`, `CHECK_IN_CANCELLED`).  
- [ ] Create DTOs in `backend/src/main/java/com/skyhigh/dto/notification/`:
  - [ ] `NotificationMessage.java` (id, type, recipient, subject, body, metadata, createdAt).  
  - [ ] Optional smaller payload types per event if needed (e.g., `WaitlistAssignedNotificationPayload`).  
- [ ] Define `NotificationService` interface in `backend/src/main/java/com/skyhigh/service/NotificationService.java` with methods such as:
  - [ ] `void sendWaitlistAssignedNotification(WaitlistEntry entry)`  
  - [ ] `void sendCheckInConfirmedNotification(CheckIn checkIn)`  
  - [ ] `void sendCheckInCancelledNotification(CheckIn checkIn)`

### 3.2 Redis Queue Publisher Implementation
- [ ] Add Spring Data Redis dependency to `backend/pom.xml` if not already present:
  - `spring-boot-starter-data-redis`  
- [ ] Create Redis configuration in `backend/src/main/java/com/skyhigh/config/RedisNotificationConfig.java`:
  - [ ] Configure `LettuceConnectionFactory` using `REDIS_HOST` / `REDIS_PORT`.  
  - [ ] Configure `RedisTemplate<String, String>` or `RedisTemplate<String, NotificationMessage>` (with JSON serialization).  
  - [ ] Externalize queue/stream name via `application.yml` (e.g., `notifications.queueName=notifications:queue`).  
- [ ] Implement `RedisNotificationService` in `backend/src/main/java/com/skyhigh/service/impl/RedisNotificationService.java`:
  - [ ] Serialize `NotificationMessage` to JSON.  
  - [ ] Push message to Redis (e.g., `LPUSH`/`RPUSH` on a List or `XADD` on a Stream).  
  - [ ] Log enqueue success/failure with message id and type.  
  - [ ] Handle Redis connectivity errors with clear exceptions and logs.

### 3.3 Integration with Domain Services
- [ ] Identify current locations where notifications are (or will be) triggered:
  - [ ] Waitlist assignment logic (`WaitlistServiceImpl`).  
  - [ ] Check-in completion (`CheckInServiceImpl`).  
  - [ ] Check-in cancellation flow.  
- [ ] Replace direct notification calls (e.g., direct email service) with `NotificationService` calls.  
- [ ] Ensure that:
  - [ ] Core domain transaction completes even if enqueue fails is handled according to business rules (decide: fail transaction vs. log-and-continue).  
  - [ ] For MVP, treat enqueue failure as non-fatal but log as ERROR; consider audit logging for missed notifications.  
- [ ] Include key data in the payload:
  - [ ] Passenger identifier and email address.  
  - [ ] Flight id and seat number.  
  - [ ] Explicit message template key / subject for the worker.

### 3.4 Optional: Basic Worker Stub (Non-blocking)
(Implementation can be minimal, but documenting here for completeness.)
- [ ] Create a simple Spring `@Component` that can run as a separate profile (`notifications-worker`) to:
  - [ ] Poll or block-pop (`BRPOP`) from the Redis queue.  
  - [ ] Deserialize `NotificationMessage`.  
  - [ ] Log the intended notification instead of calling a real provider (MVP stub).  
- [ ] Ensure worker can be disabled in default `dev` profile (feature flag).

---

## 4. Configuration & Environment

- [ ] Add properties to `backend/src/main/resources/application.yml`:
  - [ ] `notifications.enabled=true`  
  - [ ] `notifications.queueName=notifications:queue`  
- [ ] Document environment variables in README:
  - [ ] `REDIS_HOST`, `REDIS_PORT` already used; explain they also back notifications.  
- [ ] Ensure `docker-compose.yml` remains valid (backend already depends on `redis`).  

---

## 5. Testing Requirements

### 5.1 Unit Tests
- [ ] Unit tests for `RedisNotificationService`:
  - [ ] Message serialization to JSON matches expected schema.  
  - [ ] Redis template is called with correct queue key and payload.  
  - [ ] Error handling when Redis is unavailable (e.g., logs error, throws appropriate exception).  
- [ ] Unit tests for domain services:
  - [ ] `WaitlistServiceImpl` invokes `NotificationService` when a passenger is assigned a seat.  
  - [ ] `CheckInServiceImpl` invokes `NotificationService` on check-in completion and cancellation.

### 5.2 Integration / Functional Tests
- [ ] Spring Boot test using an embedded or Testcontainers Redis instance:
  - [ ] Enqueue a notification and verify it appears on the Redis queue.  
  - [ ] Optional: Start the worker stub and verify it consumes and logs the message.  
- [ ] Verify that check-in and waitlist API endpoints still meet PRD timing guarantees (enqueue must be fast).

### 5.3 Manual Testing
- [ ] Run the stack via `docker compose up`.  
- [ ] Trigger:
  - [ ] Waitlist assignment scenario (seat becomes available, next passenger gets assigned).  
  - [ ] Check-in completion.  
  - [ ] Check-in cancellation.  
- [ ] Use `redis-cli` inside the Redis container to inspect queue contents:
  - [ ] `LRANGE notifications:queue 0 -1` or equivalent.  
- [ ] Confirm payloads contain expected fields and no secrets.

---

## 6. Success Criteria

- [ ] All notification-related flows **publish messages to Redis** instead of directly invoking an external notification provider.  
- [ ] Core domain flows (waitlist assignment, check-in completion/cancellation) remain within existing performance SLAs.  
- [ ] Clear logging and basic metrics exist for enqueue operations (success/failure counts).  
- [ ] Tests cover primary enqueue paths and at least one end-to-end flow with Redis.  
- [ ] README / architecture docs updated to describe the Redis-backed notification queue.

---

**Task Status**: Ready for Implementation  
**Next Action**: Create `NotificationService` interface and Redis-backed implementation, then wire into waitlist and check-in flows.

