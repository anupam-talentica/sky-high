-- Create check_ins table
CREATE TABLE check_ins (
    check_in_id VARCHAR(50) PRIMARY KEY,
    passenger_id VARCHAR(20) NOT NULL,
    flight_id VARCHAR(20) NOT NULL,
    seat_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'pending',
    check_in_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    cancelled_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (passenger_id) REFERENCES passengers(passenger_id) ON DELETE CASCADE,
    FOREIGN KEY (flight_id) REFERENCES flights(flight_id) ON DELETE CASCADE,
    FOREIGN KEY (seat_id) REFERENCES seats(seat_id) ON DELETE CASCADE,
    CONSTRAINT chk_check_in_status CHECK (status IN ('pending', 'baggage_added', 'payment_completed', 'completed', 'cancelled'))
);

-- Create index on passenger_id and flight_id for user check-in lookup
CREATE INDEX idx_passenger_flight ON check_ins(passenger_id, flight_id);

-- Create index on status for filtering
CREATE INDEX idx_check_in_status ON check_ins(status);

-- Create index on check_in_time
CREATE INDEX idx_check_in_time ON check_ins(check_in_time);
