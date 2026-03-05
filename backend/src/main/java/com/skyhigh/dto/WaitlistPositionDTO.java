package com.skyhigh.dto;

import com.skyhigh.enums.WaitlistStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WaitlistPositionDTO {
    
    private Long waitlistId;
    private String flightId;
    private String seatNumber;
    private Integer position;
    private Long totalWaiting;
    private WaitlistStatus status;
    private String message;
}
