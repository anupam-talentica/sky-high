package com.skyhigh.exception;

public class WaitlistNotFoundException extends RuntimeException {
    
    public WaitlistNotFoundException(String message) {
        super(message);
    }
    
    public WaitlistNotFoundException(Long waitlistId) {
        super("Waitlist entry not found with ID: " + waitlistId);
    }
    
    public WaitlistNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
