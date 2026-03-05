-- Create waitlist table
CREATE TABLE waitlist (
    waitlist_id BIGSERIAL PRIMARY KEY,
    passenger_id VARCHAR(20) NOT NULL,
    flight_id VARCHAR(20) NOT NULL,
    seat_number VARCHAR(5) NOT NULL,
    position INT NOT NULL,
    status VARCHAR(20) DEFAULT 'waiting',
    joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    notified_at TIMESTAMP,
    assigned_at TIMESTAMP,
    expired_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (passenger_id) REFERENCES passengers(passenger_id) ON DELETE CASCADE,
    FOREIGN KEY (flight_id) REFERENCES flights(flight_id) ON DELETE CASCADE,
    CONSTRAINT chk_waitlist_status CHECK (status IN ('waiting', 'notified', 'assigned', 'expired', 'cancelled'))
);

-- Create index on flight_id, seat_number, and status for waitlist queries
CREATE INDEX idx_flight_seat_status ON waitlist(flight_id, seat_number, status);

-- Create index on position for FIFO ordering
CREATE INDEX idx_waitlist_position ON waitlist(position);

-- Create index on passenger_id for user waitlist lookup
CREATE INDEX idx_waitlist_passenger ON waitlist(passenger_id);
