-- V9__insert_test_users_and_flights.sql
-- Inserts additional test passengers and flights for local/testing environments.
-- NOTE: All test passengers use the same demo password hash as in V8__insert_sample_data.sql
-- Password: demo123 (BCrypt hash)

-- Insert additional passengers (idempotent via ON CONFLICT)
INSERT INTO passengers (passenger_id, first_name, last_name, email, phone, password_hash, date_of_birth, nationality, passport_number)
VALUES 
    ('P234567', 'Brian', 'Lee', 'brian.lee@example.com', '+1-555-0202', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '1984-04-22', 'USA', 'US555002222'),
    ('P345678', 'Carla', 'Mendes', 'carla.mendes@example.com', '+1-555-0203', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '1992-09-08', 'BRA', 'BR555003333'),
    ('P456789', 'David', 'Kumar', 'david.kumar@example.com', '+1-555-0204', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '1987-12-03', 'IND', 'IN555004444'),
    ('P567890', 'Emma', 'Rossi', 'emma.rossi@example.com', '+1-555-0205', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '1995-06-19', 'ITA', 'IT555005555')
ON CONFLICT (passenger_id) DO NOTHING;

-- Insert test flights (idempotent via ON CONFLICT)
INSERT INTO flights (flight_id, flight_number, departure_airport, arrival_airport, departure_time, arrival_time, aircraft_type, total_seats, status)
VALUES
    -- Flights for P123456 (JFK -> LAX)
    ('SK1001', 'SK1001', 'JFK', 'LAX', '2026-03-05T16:05:00', '2026-03-05T22:05:00', 'Boeing 737-800', 189, 'scheduled'),
    ('SK1002', 'SK1002', 'JFK', 'LAX', '2026-03-06T09:30:00', '2026-03-06T15:30:00', 'Airbus A320', 180, 'scheduled'),
    ('SK1003', 'SK1003', 'JFK', 'LAX', '2026-03-07T20:45:00', '2026-03-08T02:45:00', 'Boeing 777-200', 300, 'scheduled'),
    ('SK1004', 'SK1004', 'JFK', 'LAX', '2026-03-08T12:15:00', '2026-03-08T18:15:00', 'Boeing 737 MAX 9', 189, 'scheduled'),

    -- Flights for P234567 (SFO -> ORD)
    ('SK2001', 'SK2001', 'SFO', 'ORD', '2026-03-05T07:15:00', '2026-03-05T13:25:00', 'Boeing 737-900', 189, 'scheduled'),
    ('SK2002', 'SK2002', 'SFO', 'ORD', '2026-03-06T11:45:00', '2026-03-06T17:55:00', 'Airbus A321', 190, 'scheduled'),
    ('SK2003', 'SK2003', 'SFO', 'ORD', '2026-03-07T14:20:00', '2026-03-07T20:30:00', 'Boeing 757-200', 200, 'scheduled'),
    ('SK2004', 'SK2004', 'SFO', 'ORD', '2026-03-08T18:00:00', '2026-03-08T23:59:00', 'Boeing 737-800', 189, 'scheduled'),

    -- Flights for P345678 (SEA -> JFK)
    ('SK3001', 'SK3001', 'SEA', 'JFK', '2026-03-05T06:55:00', '2026-03-05T15:10:00', 'Boeing 737-800', 189, 'scheduled'),
    ('SK3002', 'SK3002', 'SEA', 'JFK', '2026-03-06T13:35:00', '2026-03-06T21:50:00', 'Airbus A321neo', 190, 'scheduled'),
    ('SK3003', 'SK3003', 'SEA', 'JFK', '2026-03-07T09:10:00', '2026-03-07T17:25:00', 'Boeing 757-300', 220, 'scheduled'),
    ('SK3004', 'SK3004', 'SEA', 'JFK', '2026-03-08T21:00:00', '2026-03-09T05:15:00', 'Boeing 767-300', 230, 'scheduled'),

    -- Flights for P456789 (BOS -> MIA)
    ('SK4001', 'SK4001', 'BOS', 'MIA', '2026-03-05T08:40:00', '2026-03-05T12:45:00', 'Airbus A320', 180, 'scheduled'),
    ('SK4002', 'SK4002', 'BOS', 'MIA', '2026-03-06T15:20:00', '2026-03-06T19:25:00', 'Boeing 737-800', 189, 'scheduled'),
    ('SK4003', 'SK4003', 'BOS', 'MIA', '2026-03-07T11:10:00', '2026-03-07T15:15:00', 'Airbus A321', 190, 'scheduled'),
    ('SK4004', 'SK4004', 'BOS', 'MIA', '2026-03-08T19:00:00', '2026-03-08T23:05:00', 'Boeing 737 MAX 8', 189, 'scheduled'),

    -- Flights for P567890 (LAX -> HNL)
    ('SK5001', 'SK5001', 'LAX', 'HNL', '2026-03-05T10:00:00', '2026-03-05T13:30:00', 'Airbus A330-200', 260, 'scheduled'),
    ('SK5002', 'SK5002', 'LAX', 'HNL', '2026-03-06T16:45:00', '2026-03-06T20:15:00', 'Boeing 757-200', 200, 'scheduled'),
    ('SK5003', 'SK5003', 'LAX', 'HNL', '2026-03-07T08:20:00', '2026-03-07T11:50:00', 'Boeing 767-300', 230, 'scheduled'),
    ('SK5004', 'SK5004', 'LAX', 'HNL', '2026-03-08T21:30:00', '2026-03-09T01:00:00', 'Boeing 787-8', 240, 'scheduled')
ON CONFLICT (flight_id) DO NOTHING;

