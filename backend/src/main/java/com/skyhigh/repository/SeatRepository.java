package com.skyhigh.repository;

import com.skyhigh.entity.Seat;
import com.skyhigh.enums.SeatState;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SeatRepository extends JpaRepository<Seat, Long> {

    List<Seat> findByFlightId(String flightId);

    List<Seat> findByFlightIdAndState(String flightId, SeatState state);

    Optional<Seat> findByFlightIdAndSeatNumber(String flightId, String seatNumber);

    @Lock(LockModeType.OPTIMISTIC)
    @Query("SELECT s FROM Seat s WHERE s.flightId = :flightId AND s.seatNumber = :seatNumber")
    Optional<Seat> findByFlightIdAndSeatNumberWithLock(
        @Param("flightId") String flightId,
        @Param("seatNumber") String seatNumber
    );

    @Query("SELECT s FROM Seat s WHERE s.flightId = :flightId AND s.state = 'AVAILABLE' ORDER BY s.seatNumber")
    List<Seat> findAvailableSeats(@Param("flightId") String flightId);

    @Query("SELECT COUNT(s) FROM Seat s WHERE s.flightId = :flightId AND s.state = :state")
    long countByFlightIdAndState(
        @Param("flightId") String flightId,
        @Param("state") SeatState state
    );

    @Query("SELECT s FROM Seat s WHERE s.heldUntil < :now AND s.state = 'HELD'")
    List<Seat> findExpiredHeldSeats(@Param("now") LocalDateTime now);

    @Modifying
    @Query("UPDATE Seat s SET s.state = 'AVAILABLE', s.heldBy = NULL, s.heldUntil = NULL WHERE s.heldUntil < :now AND s.state = 'HELD'")
    int releaseExpiredSeats(@Param("now") LocalDateTime now);

    List<Seat> findByConfirmedBy(String passengerId);

    List<Seat> findByHeldBy(String passengerId);

    @Query("SELECT s FROM Seat s WHERE s.flightId = :flightId AND s.state = 'CONFIRMED' ORDER BY s.seatNumber")
    List<Seat> findConfirmedSeatsByFlight(@Param("flightId") String flightId);
}
