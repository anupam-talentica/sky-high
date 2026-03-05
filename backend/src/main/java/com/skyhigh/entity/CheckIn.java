package com.skyhigh.entity;

import com.skyhigh.enums.CheckInStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "check_ins",
       indexes = {
           @Index(name = "idx_passenger_flight", columnList = "passenger_id, flight_id"),
           @Index(name = "idx_check_in_status", columnList = "status"),
           @Index(name = "idx_check_in_time", columnList = "check_in_time")
       })
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CheckIn {

    @Id
    @Column(name = "check_in_id", length = 50)
    private String checkInId;

    @Column(name = "passenger_id", nullable = false, length = 20)
    private String passengerId;

    @Column(name = "flight_id", nullable = false, length = 20)
    private String flightId;

    @Column(name = "seat_id")
    private Long seatId;

    @Convert(converter = CheckInStatusConverter.class)
    @Column(name = "status", nullable = false, length = 20)
    private CheckInStatus status = CheckInStatus.PENDING;

    @Column(name = "check_in_time")
    private LocalDateTime checkInTime;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (checkInTime == null) {
            checkInTime = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
