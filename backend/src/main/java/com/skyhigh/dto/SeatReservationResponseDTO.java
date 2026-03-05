package com.skyhigh.dto;

import com.skyhigh.enums.SeatState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeatReservationResponseDTO {
    
    private Long seatId;
    private String flightId;
    private String seatNumber;
    private SeatState state;
    private String heldBy;
    private LocalDateTime heldUntil;
    private int holdDurationSeconds;
    private String message;
}
