package com.skyhigh.entity;

import com.skyhigh.enums.WaitlistStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "waitlist",
       indexes = {
           @Index(name = "idx_flight_seat_status", columnList = "flight_id, seat_number, status"),
           @Index(name = "idx_waitlist_position", columnList = "position"),
           @Index(name = "idx_waitlist_passenger", columnList = "passenger_id")
       })
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Waitlist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "waitlist_id")
    private Long waitlistId;

    @Column(name = "passenger_id", nullable = false, length = 20)
    private String passengerId;

    @Column(name = "flight_id", nullable = false, length = 20)
    private String flightId;

    @Column(name = "seat_number", nullable = false, length = 5)
    private String seatNumber;

    @Column(name = "position", nullable = false)
    private Integer position;

    @Convert(converter = WaitlistStatusConverter.class)
    @Column(name = "status", nullable = false, length = 20)
    private WaitlistStatus status = WaitlistStatus.WAITING;

    @Column(name = "joined_at")
    private LocalDateTime joinedAt;

    @Column(name = "notified_at")
    private LocalDateTime notifiedAt;

    @Column(name = "assigned_at")
    private LocalDateTime assignedAt;

    @Column(name = "expired_at")
    private LocalDateTime expiredAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (joinedAt == null) {
            joinedAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
