package com.skyhigh.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlightStatusDTO {

    /**
     * Internal flight identifier (UUID-like string used in our DB).
     */
    @NotBlank
    private String flightId;

    /**
     * IATA flight number, e.g. "SK1234".
     */
    @NotBlank
    @Pattern(regexp = "^[A-Z0-9]{2}\\d{1,4}$")
    private String flightNumber;

    /**
     * Normalized flight status: scheduled, active, landed, cancelled, diverted, delayed, departed, arrived.
     */
    @NotBlank
    private String status;

    private DepartureInfoDTO departure;

    private ArrivalInfoDTO arrival;

    private DelayInfoDTO delay;

    private LocalDateTime lastUpdated;
}

