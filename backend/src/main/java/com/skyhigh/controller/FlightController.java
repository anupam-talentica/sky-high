package com.skyhigh.controller;

import com.skyhigh.dto.FlightDTO;
import com.skyhigh.dto.FlightListResponseDTO;
import com.skyhigh.dto.FlightStatusDTO;
import com.skyhigh.service.FlightService;
import com.skyhigh.service.FlightStatusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.constraints.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/flights")
@Slf4j
public class FlightController {

    private final FlightService flightService;
    private final FlightStatusService flightStatusService;

    public FlightController(FlightService flightService, FlightStatusService flightStatusService) {
        this.flightService = flightService;
        this.flightStatusService = flightStatusService;
    }

    @GetMapping
    public ResponseEntity<FlightListResponseDTO> getAllFlights() {
        FlightListResponseDTO response = flightService.getAllFlights();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{flightId}")
    public ResponseEntity<FlightDTO> getFlightById(@PathVariable String flightId) {
        FlightDTO flight = flightService.getFlightById(flightId);
        return ResponseEntity.ok(flight);
    }

    @GetMapping("/{flightNumber}/status")
    @Operation(summary = "Get real-time flight status")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Flight status retrieved"),
            @ApiResponse(responseCode = "404", description = "Flight not found"),
            @ApiResponse(responseCode = "503", description = "Service unavailable")
    })
    public ResponseEntity<FlightStatusDTO> getFlightStatus(
            @PathVariable
            @Pattern(regexp = "^[A-Z0-9]{2}\\d{1,4}$", message = "Invalid flight number format")
            String flightNumber) {

        log.info("Flight status request for: {}", flightNumber);
        FlightStatusDTO status = flightStatusService.getFlightStatusWithFallback(flightNumber);
        return ResponseEntity.ok(status);
    }

    @GetMapping("/search")
    public ResponseEntity<FlightListResponseDTO> searchFlights(
            @RequestParam(required = false) String origin,
            @RequestParam(required = false) String destination
    ) {
        FlightListResponseDTO response = flightService.searchFlights(origin, destination);
        return ResponseEntity.ok(response);
    }
}
