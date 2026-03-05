-- V10__insert_seats_for_test_flights.sql
-- Generate basic seat maps for the additional test flights introduced in V9.
-- For simplicity, we create a uniform 30-row, 6-seat-per-row economy layout (A–F) for each flight.

INSERT INTO seats (flight_id, seat_number, seat_type, state)
SELECT f.flight_id,
       gs.seat_number,
       CASE
           WHEN RIGHT(gs.seat_number, 1) IN ('A', 'F') THEN 'window'
           WHEN RIGHT(gs.seat_number, 1) IN ('C', 'D') THEN 'aisle'
           ELSE 'middle'
       END AS seat_type,
       'AVAILABLE'
FROM flights f
JOIN (
    SELECT row_num || seat_letter AS seat_number
    FROM generate_series(1, 30) AS row_num,
         unnest(ARRAY['A', 'B', 'C', 'D', 'E', 'F']) AS seat_letter
) gs ON f.flight_id IN (
    'SK1001', 'SK1002', 'SK1003', 'SK1004',
    'SK2001', 'SK2002', 'SK2003', 'SK2004',
    'SK3001', 'SK3002', 'SK3003', 'SK3004',
    'SK4001', 'SK4002', 'SK4003', 'SK4004',
    'SK5001', 'SK5002', 'SK5003', 'SK5004'
)
ON CONFLICT (flight_id, seat_number) DO NOTHING;

