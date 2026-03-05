package com.skyhigh.exception;

/**
 * Thrown when a distributed seat lock cannot be acquired (another node is reserving the seat).
 * Handled by {@link com.skyhigh.exception.GlobalExceptionHandler} with 503 and Retry-After header.
 */
public class SeatLockConflictException extends RuntimeException {

    private final int retryAfterSeconds;

    public SeatLockConflictException(int retryAfterSeconds) {
        super("Seat is being reserved by another request. Please retry in " + retryAfterSeconds + " seconds.");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public int getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
