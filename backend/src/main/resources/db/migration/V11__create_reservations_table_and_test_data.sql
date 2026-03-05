-- Create reservations table to store passenger-flight associations
CREATE TABLE reservations (
    reservation_id BIGSERIAL PRIMARY KEY,
    flight_id VARCHAR(20) NOT NULL,
    passenger_id VARCHAR(20) NOT NULL,
    booking_reference VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (flight_id) REFERENCES flights(flight_id) ON DELETE CASCADE,
    FOREIGN KEY (passenger_id) REFERENCES passengers(passenger_id) ON DELETE CASCADE,
    CONSTRAINT chk_reservation_status CHECK (status IN ('ACTIVE', 'CANCELLED')),
    CONSTRAINT unique_reservation UNIQUE (flight_id, passenger_id)
);

-- Indexes for efficient lookup by passenger and flight
CREATE INDEX idx_reservation_passenger ON reservations(passenger_id);
CREATE INDEX idx_reservation_flight ON reservations(flight_id);

-- Seed reservations for existing dummy passengers and flights
-- Map initial sample passenger to FL001
INSERT INTO reservations (flight_id, passenger_id, booking_reference, status)
VALUES
    ('FL001', 'P123456', 'BR-FL001-P123456', 'ACTIVE')
ON CONFLICT (flight_id, passenger_id) DO NOTHING;

-- Map test passengers from V9__insert_test_users_and_flights.sql
-- P123456 -> SK1001-SK1004
INSERT INTO reservations (flight_id, passenger_id, booking_reference, status)
VALUES
    ('SK1001', 'P123456', 'BR-SK1001-P123456', 'ACTIVE'),
    ('SK1002', 'P123456', 'BR-SK1002-P123456', 'ACTIVE'),
    ('SK1003', 'P123456', 'BR-SK1003-P123456', 'ACTIVE'),
    ('SK1004', 'P123456', 'BR-SK1004-P123456', 'ACTIVE')
ON CONFLICT (flight_id, passenger_id) DO NOTHING;

-- P234567 -> SK2001-SK2004
INSERT INTO reservations (flight_id, passenger_id, booking_reference, status)
VALUES
    ('SK2001', 'P234567', 'BR-SK2001-P234567', 'ACTIVE'),
    ('SK2002', 'P234567', 'BR-SK2002-P234567', 'ACTIVE'),
    ('SK2003', 'P234567', 'BR-SK2003-P234567', 'ACTIVE'),
    ('SK2004', 'P234567', 'BR-SK2004-P234567', 'ACTIVE')
ON CONFLICT (flight_id, passenger_id) DO NOTHING;

-- P345678 -> SK3001-SK3004
INSERT INTO reservations (flight_id, passenger_id, booking_reference, status)
VALUES
    ('SK3001', 'P345678', 'BR-SK3001-P345678', 'ACTIVE'),
    ('SK3002', 'P345678', 'BR-SK3002-P345678', 'ACTIVE'),
    ('SK3003', 'P345678', 'BR-SK3003-P345678', 'ACTIVE'),
    ('SK3004', 'P345678', 'BR-SK3004-P345678', 'ACTIVE')
ON CONFLICT (flight_id, passenger_id) DO NOTHING;

-- P456789 -> SK4001-SK4004
INSERT INTO reservations (flight_id, passenger_id, booking_reference, status)
VALUES
    ('SK4001', 'P456789', 'BR-SK4001-P456789', 'ACTIVE'),
    ('SK4002', 'P456789', 'BR-SK4002-P456789', 'ACTIVE'),
    ('SK4003', 'P456789', 'BR-SK4003-P456789', 'ACTIVE'),
    ('SK4004', 'P456789', 'BR-SK4004-P456789', 'ACTIVE')
ON CONFLICT (flight_id, passenger_id) DO NOTHING;

-- P567890 -> SK5001-SK5004
INSERT INTO reservations (flight_id, passenger_id, booking_reference, status)
VALUES
    ('SK5001', 'P567890', 'BR-SK5001-P567890', 'ACTIVE'),
    ('SK5002', 'P567890', 'BR-SK5002-P567890', 'ACTIVE'),
    ('SK5003', 'P567890', 'BR-SK5003-P567890', 'ACTIVE'),
    ('SK5004', 'P567890', 'BR-SK5004-P567890', 'ACTIVE')
ON CONFLICT (flight_id, passenger_id) DO NOTHING;

