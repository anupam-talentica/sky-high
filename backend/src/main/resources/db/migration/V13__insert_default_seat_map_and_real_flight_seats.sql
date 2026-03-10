-- V13__insert_default_seat_map_and_real_flight_seats.sql
-- Create a shared DEFAULT seat map and reuse it for real AviationStack flights
-- that do not have explicit per-flight seat maps defined.

-- 1) Create a DEFAULT logical flight with a generic route if it does not exist.
INSERT INTO flights (flight_id, flight_number, departure_airport, arrival_airport, departure_time, arrival_time, aircraft_type, total_seats, status)
VALUES (
    'DEFAULT',
    'DEFAULT',
    'XXX',
    'YYY',
    '2026-01-01T00:00:00',
    '2026-01-01T01:00:00',
    'Generic Narrowbody',
    180,
    'scheduled'
)
ON CONFLICT (flight_id) DO NOTHING;

-- 2) Populate a uniform 30x6 economy seat map for the DEFAULT flight.
INSERT INTO seats (flight_id, seat_number, seat_type, state)
SELECT 'DEFAULT' AS flight_id,
       gs.seat_number,
       CASE
           WHEN RIGHT(gs.seat_number, 1) IN ('A', 'F') THEN 'window'
           WHEN RIGHT(gs.seat_number, 1) IN ('C', 'D') THEN 'aisle'
           ELSE 'middle'
       END AS seat_type,
       'AVAILABLE'
FROM (
    SELECT row_num || seat_letter AS seat_number
    FROM generate_series(1, 30) AS row_num,
         unnest(ARRAY['A', 'B', 'C', 'D', 'E', 'F']) AS seat_letter
) gs
ON CONFLICT (flight_id, seat_number) DO NOTHING;

-- 3) Give the real AviationStack flights (AF381, NH885, SQ282) their own
--    concrete seats by copying the DEFAULT template. This ensures that
--    seat counts and reservations work per flight, while the layout is shared.
INSERT INTO seats (flight_id, seat_number, seat_type, state)
SELECT f.flight_id,
       s.seat_number,
       s.seat_type,
       s.state
FROM flights f
JOIN seats s ON s.flight_id = 'DEFAULT'
WHERE f.flight_id IN ('AF381', 'NH885', 'SQ282')
ON CONFLICT (flight_id, seat_number) DO NOTHING;

