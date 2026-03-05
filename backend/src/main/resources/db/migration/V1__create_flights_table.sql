-- Create flights table
CREATE TABLE flights (
    flight_id VARCHAR(20) PRIMARY KEY,
    flight_number VARCHAR(10) NOT NULL,
    departure_airport VARCHAR(3) NOT NULL,
    arrival_airport VARCHAR(3) NOT NULL,
    departure_time TIMESTAMP NOT NULL,
    arrival_time TIMESTAMP NOT NULL,
    aircraft_type VARCHAR(50) NOT NULL,
    total_seats INT NOT NULL,
    status VARCHAR(20) DEFAULT 'scheduled',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_flight_status CHECK (status IN ('scheduled', 'boarding', 'departed', 'arrived', 'cancelled'))
);

-- Create index on departure time for efficient queries
CREATE INDEX idx_departure_time ON flights(departure_time);

-- Create index on flight number
CREATE INDEX idx_flight_number ON flights(flight_number);
