package com.skyhigh.exception;

public class WaitlistAlreadyExistsException extends RuntimeException {
    
    public WaitlistAlreadyExistsException(String message) {
        super(message);
    }
    
    public WaitlistAlreadyExistsException(String passengerId, String flightId, String seatNumber) {
        super(String.format("Passenger %s is already on waitlist for seat %s on flight %s", 
            passengerId, seatNumber, flightId));
    }
    
    public WaitlistAlreadyExistsException(String message, Throwable cause) {
        super(message, cause);
    }
}
