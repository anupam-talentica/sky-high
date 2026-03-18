package com.skyhigh.service;

import com.skyhigh.dto.AirportDTO;

public interface WeatherService {

    /**
     * Enrich the given airport DTO with current weather information (temperature in Celsius, description)
     * based on the airport's timezone and coordinates, if available.
     *
     * Implementations should swallow remote API failures and simply return the original DTO
     * when weather information cannot be retrieved.
     */
    AirportDTO enrichWithCurrentWeather(AirportDTO airport);
}

