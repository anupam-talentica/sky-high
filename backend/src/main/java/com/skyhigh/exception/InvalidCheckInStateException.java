package com.skyhigh.exception;

public class InvalidCheckInStateException extends RuntimeException {
    
    public InvalidCheckInStateException(String message) {
        super(message);
    }
    
    public InvalidCheckInStateException(String message, Throwable cause) {
        super(message, cause);
    }
}
