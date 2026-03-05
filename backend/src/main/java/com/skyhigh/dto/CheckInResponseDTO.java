package com.skyhigh.dto;

import com.skyhigh.enums.CheckInStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheckInResponseDTO {

    private String checkInId;
    private String passengerId;
    private String flightId;
    private Long seatId;
    private String seatNumber;
    private CheckInStatus status;
    private LocalDateTime checkInTime;
    private LocalDateTime completedAt;
    private LocalDateTime cancelledAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String message;
    private List<BaggageResponseDTO> baggageDetails;
    private BigDecimal totalBaggageFee;
    private String boardingPass;
}
