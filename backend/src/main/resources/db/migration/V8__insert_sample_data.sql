-- Insert hardcoded passengers
-- Password: demo123 (BCrypt hash)
INSERT INTO passengers (passenger_id, first_name, last_name, email, phone, password_hash, date_of_birth, nationality, passport_number)
VALUES 
    ('P123456', 'John', 'Doe', 'john@example.com', '+1-555-0101', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '1985-03-15', 'USA', 'US123456789'),
    ('P789012', 'Jane', 'Smith', 'jane@example.com', '+1-555-0102', '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi', '1990-07-22', 'USA', 'US987654321');

-- Insert sample flight: SK1234 (JFK → LAX)
INSERT INTO flights (flight_id, flight_number, departure_airport, arrival_airport, departure_time, arrival_time, aircraft_type, total_seats, status)
VALUES 
    ('FL001', 'SK1234', 'JFK', 'LAX', CURRENT_TIMESTAMP + INTERVAL '2 days', CURRENT_TIMESTAMP + INTERVAL '2 days 6 hours', 'Boeing 737-800', 189, 'scheduled');

-- Insert 189 seats for Boeing 737-800
-- First Class: Rows 1-3 (12 seats: A, B, C, D per row)
-- Economy: Rows 4-32 (177 seats: A, B, C, D, E, F per row, except last row with 3 seats)

-- First Class (Rows 1-3, 4 seats per row = 12 seats)
INSERT INTO seats (flight_id, seat_number, seat_type, state)
SELECT 
    'FL001',
    row_num || seat_letter,
    CASE 
        WHEN seat_letter IN ('A', 'D') THEN 'window'
        WHEN seat_letter IN ('B', 'C') THEN 'aisle'
    END,
    'AVAILABLE'
FROM 
    generate_series(1, 3) AS row_num,
    unnest(ARRAY['A', 'B', 'C', 'D']) AS seat_letter;

-- Economy (Rows 4-31, 6 seats per row = 168 seats)
INSERT INTO seats (flight_id, seat_number, seat_type, state)
SELECT 
    'FL001',
    row_num || seat_letter,
    CASE 
        WHEN seat_letter IN ('A', 'F') THEN 'window'
        WHEN seat_letter IN ('C', 'D') THEN 'aisle'
        WHEN seat_letter IN ('B', 'E') THEN 'middle'
    END,
    'AVAILABLE'
FROM 
    generate_series(4, 31) AS row_num,
    unnest(ARRAY['A', 'B', 'C', 'D', 'E', 'F']) AS seat_letter;

-- Last row (Row 32, 3 seats = 3 seats)
INSERT INTO seats (flight_id, seat_number, seat_type, state)
SELECT 
    'FL001',
    '32' || seat_letter,
    CASE 
        WHEN seat_letter = 'A' THEN 'window'
        WHEN seat_letter = 'B' THEN 'middle'
        WHEN seat_letter = 'C' THEN 'aisle'
    END,
    'AVAILABLE'
FROM 
    unnest(ARRAY['A', 'B', 'C']) AS seat_letter;

-- Insert audit log for data initialization
INSERT INTO audit_logs (entity_type, entity_id, action, new_state, user_id, details)
VALUES 
    ('SYSTEM', 'INIT', 'DATA_INITIALIZATION', '{"passengers": 2, "flights": 1, "seats": 189}'::jsonb, 'SYSTEM', 'Initial sample data loaded for MVP');
