package com.skyhigh.controller;

import com.skyhigh.dto.AirlineDTO;
import com.skyhigh.dto.AirportDTO;
import com.skyhigh.service.AviationMetadataService;
import com.skyhigh.service.WeatherService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AviationMetadataController {

    private final AviationMetadataService aviationMetadataService;
    private final WeatherService weatherService;

    @GetMapping("/airlines/{iataCode}")
    @Operation(summary = "Get airline details by IATA code")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Airline details retrieved"),
            @ApiResponse(responseCode = "400", description = "Invalid IATA code"),
            @ApiResponse(responseCode = "503", description = "Service unavailable")
    })
    public ResponseEntity<AirlineDTO> getAirlineByIata(
            @PathVariable
            @Pattern(regexp = "^[A-Z0-9]{2}$", message = "Invalid airline IATA code")
            String iataCode) {
        AirlineDTO dto = aviationMetadataService.getAirlineByIata(iataCode);
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/airports/{iataCode}")
    @Operation(summary = "Get airport details by IATA code")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Airport details retrieved"),
            @ApiResponse(responseCode = "400", description = "Invalid IATA code"),
            @ApiResponse(responseCode = "503", description = "Service unavailable")
    })
    public ResponseEntity<AirportDTO> getAirportByIata(
            @PathVariable
            @Pattern(regexp = "^[A-Z]{3}$", message = "Invalid airport IATA code")
            String iataCode) {
        AirportDTO dto = aviationMetadataService.getAirportByIata(iataCode);
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/airports/{iataCode}/weather")
    @Operation(summary = "Get current weather for an airport by IATA code")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Airport weather retrieved"),
            @ApiResponse(responseCode = "400", description = "Invalid IATA code"),
            @ApiResponse(responseCode = "503", description = "Service unavailable")
    })
    public ResponseEntity<AirportDTO> getAirportWeatherByIata(
            @PathVariable
            @Pattern(regexp = "^[A-Z]{3}$", message = "Invalid airport IATA code")
            String iataCode) {
        AirportDTO airport = aviationMetadataService.getAirportByIata(iataCode);
        AirportDTO enriched = weatherService.enrichWithCurrentWeather(airport);
        return ResponseEntity.ok(enriched);
    }
}

