package com.skyhigh.entity;

import com.skyhigh.enums.SeatState;
import com.skyhigh.enums.SeatType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "seats", 
       uniqueConstraints = {
           @UniqueConstraint(name = "unique_flight_seat", columnNames = {"flight_id", "seat_number"})
       },
       indexes = {
           @Index(name = "idx_flight_state", columnList = "flight_id, state"),
           @Index(name = "idx_held_until", columnList = "held_until"),
           @Index(name = "idx_confirmed_by", columnList = "confirmed_by")
       })
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "seat_id")
    private Long seatId;

    @Column(name = "flight_id", nullable = false, length = 20)
    private String flightId;

    @Column(name = "seat_number", nullable = false, length = 5)
    private String seatNumber;

    @Convert(converter = SeatTypeConverter.class)
    @Column(name = "seat_type", nullable = false, length = 20)
    private SeatType seatType;

    @Convert(converter = SeatStateConverter.class)
    @Column(name = "state", nullable = false, length = 20)
    private SeatState state = SeatState.AVAILABLE;

    @Column(name = "held_by", length = 20)
    private String heldBy;

    @Column(name = "held_until")
    private LocalDateTime heldUntil;

    @Column(name = "confirmed_by", length = 20)
    private String confirmedBy;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version = 0;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void transitionState(SeatState newState) {
        if (!state.canTransitionTo(newState)) {
            throw new IllegalStateException(
                String.format("Cannot transition from %s to %s for seat %s", 
                    state, newState, seatNumber)
            );
        }
        this.state = newState;
    }
    
    public void setState(SeatState state) {
        this.state = state;
    }

    public boolean isExpired() {
        return state == SeatState.HELD && 
               heldUntil != null && 
               LocalDateTime.now().isAfter(heldUntil);
    }
}
