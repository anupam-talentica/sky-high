package com.skyhigh.exception;

/**
 * Thrown when a passenger is not allowed to check in for a flight,
 * for example when the flight is cancelled or diverted.
 */
public class CheckInNotAllowedException extends RuntimeException {

    public CheckInNotAllowedException(String message) {
        super(message);
    }
}

