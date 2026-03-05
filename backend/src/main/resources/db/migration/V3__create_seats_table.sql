-- Create seats table with optimistic locking
CREATE TABLE seats (
    seat_id BIGSERIAL PRIMARY KEY,
    flight_id VARCHAR(20) NOT NULL,
    seat_number VARCHAR(5) NOT NULL,
    seat_type VARCHAR(20) NOT NULL,
    state VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
    held_by VARCHAR(20),
    held_until TIMESTAMP,
    confirmed_by VARCHAR(20),
    version INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (flight_id) REFERENCES flights(flight_id) ON DELETE CASCADE,
    FOREIGN KEY (held_by) REFERENCES passengers(passenger_id) ON DELETE SET NULL,
    FOREIGN KEY (confirmed_by) REFERENCES passengers(passenger_id) ON DELETE SET NULL,
    CONSTRAINT chk_seat_state CHECK (state IN ('AVAILABLE', 'HELD', 'CONFIRMED', 'CANCELLED')),
    CONSTRAINT chk_seat_type CHECK (seat_type IN ('window', 'middle', 'aisle'))
);

-- Create unique constraint on flight_id and seat_number
CREATE UNIQUE INDEX unique_flight_seat ON seats(flight_id, seat_number);

-- Create index on flight_id and state for efficient seat availability queries
CREATE INDEX idx_flight_state ON seats(flight_id, state);

-- Create index on held_until for efficient expiration checks
CREATE INDEX idx_held_until ON seats(held_until) WHERE held_until IS NOT NULL;

-- Create index on confirmed_by for passenger seat lookup
CREATE INDEX idx_confirmed_by ON seats(confirmed_by) WHERE confirmed_by IS NOT NULL;
