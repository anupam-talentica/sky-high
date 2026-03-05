package com.skyhigh.controller;

import com.skyhigh.dto.FlightDTO;
import com.skyhigh.dto.FlightListResponseDTO;
import com.skyhigh.service.FlightService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/flights")
public class FlightController {

    private final FlightService flightService;

    public FlightController(FlightService flightService) {
        this.flightService = flightService;
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

    @GetMapping("/search")
    public ResponseEntity<FlightListResponseDTO> searchFlights(
        @RequestParam(required = false) String origin,
        @RequestParam(required = false) String destination
    ) {
        FlightListResponseDTO response = flightService.searchFlights(origin, destination);
        return ResponseEntity.ok(response);
    }
}
