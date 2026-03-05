package com.skyhigh.exception;

public class SeatNotFoundException extends RuntimeException {
    
    public SeatNotFoundException(String message) {
        super(message);
    }
    
    public SeatNotFoundException(String flightId, String seatNumber) {
        super(String.format("Seat %s not found for flight %s", seatNumber, flightId));
    }
}
