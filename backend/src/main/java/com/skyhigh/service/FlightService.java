package com.skyhigh.service;

import com.skyhigh.dto.FlightDTO;
import com.skyhigh.dto.FlightListResponseDTO;

import java.util.List;

public interface FlightService {
    FlightListResponseDTO getAllFlights();
    FlightDTO getFlightById(String flightId);
    FlightListResponseDTO searchFlights(String origin, String destination);
}
