package com.skyhigh.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeatReservationRequestDTO {
    
    private Long seatId;
    
    @NotBlank(message = "Passenger ID is required")
    private String passengerId;
    
    @Pattern(regexp = "^[A-Z0-9]{2,5}$", message = "Invalid seat number format")
    private String seatNumber;
}
