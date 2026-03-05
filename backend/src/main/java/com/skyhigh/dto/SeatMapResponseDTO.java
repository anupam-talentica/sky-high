package com.skyhigh.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeatMapResponseDTO {
    
    private String flightId;
    private int totalSeats;
    private int availableSeats;
    private int heldSeats;
    private int confirmedSeats;
    private List<SeatDTO> seats;
}
