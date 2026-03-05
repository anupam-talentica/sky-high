package com.skyhigh.exception;

public class BaggageNotFoundException extends RuntimeException {
    
    public BaggageNotFoundException(String message) {
        super(message);
    }
    
    public BaggageNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
