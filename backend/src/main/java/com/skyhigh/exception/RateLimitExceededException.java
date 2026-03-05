package com.skyhigh.exception;

/**
 * Thrown when a client exceeds the configured rate limit / abuse threshold
 * for a given operation (e.g. seat map access).
 */
public class RateLimitExceededException extends RuntimeException {

    private final int retryAfterSeconds;

    public RateLimitExceededException(String message, int retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public int getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}

