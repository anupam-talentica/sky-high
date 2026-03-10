package com.skyhigh.exception;

/**
 * Thrown when a flight cannot be found either in the external provider
 * or in the local database.
 */
public class FlightNotFoundException extends RuntimeException {

    public FlightNotFoundException(String message) {
        super(message);
    }
}

