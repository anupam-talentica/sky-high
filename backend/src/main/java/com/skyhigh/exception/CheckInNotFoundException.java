package com.skyhigh.exception;

public class CheckInNotFoundException extends RuntimeException {
    
    public CheckInNotFoundException(String message) {
        super(message);
    }
    
    public CheckInNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
