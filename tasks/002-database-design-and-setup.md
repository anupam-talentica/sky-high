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
- [ ] Create `flights` table with flight information
- [ ] Create `passengers` table with hardcoded users
- [ ] Create `seats` table with state machine and version column
- [ ] Create `check_ins` table for check-in records
- [ ] Create `baggage` table for baggage information
- [ ] Create `waitlist` table for waitlist entries
- [ ] Create `audit_logs` table for state change tracking

### 2. Indexes and Constraints
- [ ] Add index on seats (flight_id, state)
- [ ] Add index on seats (held_until)
- [ ] Add unique constraint on seats (flight_id, seat_number)
- [ ] Add index on check_ins (passenger_id, flight_id)
- [ ] Add index on waitlist (flight_id, seat_number, status)
- [ ] Add CHECK constraints for state machine validation

### 3. Flyway Migrations
- [ ] Create V1__create_flights_table.sql
- [ ] Create V2__create_passengers_table.sql
- [ ] Create V3__create_seats_table.sql (with version column)
- [ ] Create V4__create_check_ins_table.sql
- [ ] Create V5__create_baggage_table.sql
- [ ] Create V6__create_waitlist_table.sql
- [ ] Create V7__create_audit_logs_table.sql
- [ ] Create V8__insert_sample_data.sql

### 4. JPA Entities
- [ ] Create Flight entity
- [ ] Create Passenger entity
- [ ] Create Seat entity with @Version annotation
- [ ] Create CheckIn entity
- [ ] Create Baggage entity
- [ ] Create Waitlist entity
- [ ] Create AuditLog entity
- [ ] Implement state machine validation in Seat entity

### 5. Repositories
- [ ] Create FlightRepository with custom queries
- [ ] Create PassengerRepository
- [ ] Create SeatRepository with optimistic locking
- [ ] Create CheckInRepository
- [ ] Create BaggageRepository
- [ ] Create WaitlistRepository
- [ ] Create AuditLogRepository

### 6. Sample Data
- [ ] Insert hardcoded passengers (P123456, P789012)
- [ ] Insert sample flight (SK1234: JFK → LAX)
- [ ] Insert 189 seats for Boeing 737-800
- [ ] Hash passwords with BCrypt

### 7. Connection Pooling
- [ ] Configure HikariCP in application.yml
- [ ] Set maximum pool size: 20
- [ ] Set minimum idle: 5
- [ ] Configure timeouts and max lifetime

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
