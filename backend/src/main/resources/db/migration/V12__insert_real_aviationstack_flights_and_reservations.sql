-- V12__insert_real_aviationstack_flights_and_reservations.sql
-- Seed a few real-world flights (from flights.json / AviationStack sample data)
-- and attach existing test passengers to them so the real-time integration
-- can be exercised end-to-end.

-- Insert real flights; use IATA flight code as both flight_id and flight_number
-- so that FlightStatusService can call AviationStack with the same value.
INSERT INTO flights (
    flight_id,
    flight_number,
    departure_airport,
    arrival_airport,
    departure_time,
    arrival_time,
    aircraft_type,
    total_seats,
    status
)
VALUES
    -- AF381: Beijing (PEK) -> Paris CDG
    ('AF381', 'AF381', 'PEK', 'CDG', '2026-03-07T00:05:00', '2026-03-07T06:00:00', 'Boeing 777-300', 300, 'scheduled'),

    -- NH885: Tokyo Haneda (HND) -> Kuala Lumpur (KUL)
    ('NH885', 'NH885', 'HND', 'KUL', '2026-03-07T00:05:00', '2026-03-07T06:45:00', 'Boeing 787-9', 280, 'scheduled'),

    -- SQ282: Auckland (AKL) -> Singapore (SIN)
    ('SQ282', 'SQ282', 'AKL', 'SIN', '2026-03-07T01:15:00', '2026-03-07T06:40:00', 'Airbus A350-900', 300, 'scheduled')
ON CONFLICT (flight_id) DO NOTHING;

-- Attach existing demo passengers to these real flights so they can check in.
INSERT INTO reservations (flight_id, passenger_id, booking_reference, status)
VALUES
    ('AF381', 'P123456', 'BR-AF381-P123456', 'ACTIVE'),
    ('NH885', 'P234567', 'BR-NH885-P234567', 'ACTIVE'),
    ('SQ282', 'P345678', 'BR-SQ282-P345678', 'ACTIVE')
ON CONFLICT (flight_id, passenger_id) DO NOTHING;

