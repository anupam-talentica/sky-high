package com.skyhigh.dto;

import com.skyhigh.enums.SeatState;
import com.skyhigh.enums.SeatType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeatDTO {
    
    private Long seatId;
    private String seatNumber;
    private SeatType seatType;
    private SeatState state;
    private boolean available;
    private String heldBy;
    private String confirmedBy;
}
