package com.skyhigh.dto;

import com.skyhigh.enums.CheckInStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PassengerCheckInSummaryDTO {

    private String checkInId;
    private String passengerId;
    private String flightId;
    private Long seatId;
    private CheckInStatus status;
    /**
     * Aligns with frontend \"initiatedAt\" field, backed by checkInTime in the entity.
     */
    private LocalDateTime initiatedAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

