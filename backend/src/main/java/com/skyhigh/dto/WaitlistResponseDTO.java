package com.skyhigh.dto;

import com.skyhigh.enums.WaitlistStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WaitlistResponseDTO {
    
    private Long waitlistId;
    private String passengerId;
    private String flightId;
    private String seatNumber;
    private Integer position;
    private WaitlistStatus status;
    private LocalDateTime joinedAt;
    private LocalDateTime notifiedAt;
    private LocalDateTime assignedAt;
    private LocalDateTime expiredAt;
    private String message;
}
