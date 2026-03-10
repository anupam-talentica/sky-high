package com.skyhigh.exception;

/**
 * Thrown when an external dependency (e.g. AviationStack) fails or
 * returns an unexpected response.
 */
public class ExternalServiceException extends RuntimeException {

    public ExternalServiceException(String message) {
        super(message);
    }

    public ExternalServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}

