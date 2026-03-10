package com.skyhigh.service;

import com.skyhigh.dto.FlightStatusDTO;

/**
 * Service for retrieving real-time flight status from external providers (AviationStack)
 * with automatic fallback to local database records when needed.
 */
public interface FlightStatusService {

    /**
     * Fetches real-time flight status from AviationStack API.
     *
     * @param flightNumber IATA flight number (e.g., "SK1234")
     * @return Flight status with gate, delay, and timing information
     */
    FlightStatusDTO getFlightStatus(String flightNumber);

    /**
     * Fetches flight status with automatic fallback to local database when the
     * external provider is unavailable or does not return data.
     *
     * @param flightNumber IATA flight number
     * @return Flight status from API or local database
     */
    FlightStatusDTO getFlightStatusWithFallback(String flightNumber);
}

