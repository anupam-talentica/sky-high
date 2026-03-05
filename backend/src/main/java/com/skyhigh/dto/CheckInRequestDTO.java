package com.skyhigh.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CheckInRequestDTO {

    @NotBlank(message = "Passenger ID is required")
    private String passengerId;

    @NotBlank(message = "Flight ID is required")
    private String flightId;

    private String seatNumber;
}
