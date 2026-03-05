# Task 002: Database Design and Setup

## Objective
Design and implement the complete database schema with all required tables, relationships, indexes, and constraints.

## Scope
- Create database schema for all entities
- Implement Flyway migrations
- Set up optimistic locking for concurrency control
- Configure connection pooling
- Add sample data for testing

## Key Deliverables

### 1. Database Schema
- [x] Create `flights` table with flight information
- [x] Create `passengers` table with hardcoded users
- [x] Create `seats` table with state machine and version column
- [x] Create `check_ins` table for check-in records
- [x] Create `baggage` table for baggage information
- [x] Create `waitlist` table for waitlist entries
- [x] Create `audit_logs` table for state change tracking

### 2. Indexes and Constraints
- [x] Add index on seats (flight_id, state)
- [x] Add index on seats (held_until)
- [x] Add unique constraint on seats (flight_id, seat_number)
- [x] Add index on check_ins (passenger_id, flight_id)
- [x] Add index on waitlist (flight_id, seat_number, status)
- [x] Add CHECK constraints for state machine validation

### 3. Flyway Migrations
- [x] Create V1__create_flights_table.sql
- [x] Create V2__create_passengers_table.sql
- [x] Create V3__create_seats_table.sql (with version column)
- [x] Create V4__create_check_ins_table.sql
- [x] Create V5__create_baggage_table.sql
- [x] Create V6__create_waitlist_table.sql
- [x] Create V7__create_audit_logs_table.sql
- [x] Create V8__insert_sample_data.sql

### 4. JPA Entities
- [x] Create Flight entity
- [x] Create Passenger entity
- [x] Create Seat entity with @Version annotation
- [x] Create CheckIn entity
- [x] Create Baggage entity
- [x] Create Waitlist entity
- [x] Create AuditLog entity
- [x] Implement state machine validation in Seat entity

### 5. Repositories
- [x] Create FlightRepository with custom queries
- [x] Create PassengerRepository
- [x] Create SeatRepository with optimistic locking
- [x] Create CheckInRepository
- [x] Create BaggageRepository
- [x] Create WaitlistRepository
- [x] Create AuditLogRepository

### 6. Sample Data
- [x] Insert hardcoded passengers (P123456, P789012)
- [x] Insert sample flight (SK1234: JFK → LAX)
- [x] Insert 183 seats for Boeing 737-800
- [x] Hash passwords with BCrypt

### 7. Connection Pooling
- [x] Configure HikariCP in application.yml
- [x] Set maximum pool size: 20
- [x] Set minimum idle: 5
- [x] Configure timeouts and max lifetime

## Dependencies
- PostgreSQL 15 container running
- Spring Data JPA configured
- Flyway dependency added

## Success Criteria
- All tables created successfully
- Flyway migrations run without errors
- Sample data is inserted
- JPA entities map correctly to tables
- Optimistic locking works for concurrent seat updates
- All indexes are created and functional

## Estimated Effort
High-level database setup task

## References
- TRD.md Section 5: Database Design
- PRD.md Section 10: Data Models
- database-patterns.mdc cursor rule
