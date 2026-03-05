package com.skyhigh.repository;

import com.skyhigh.entity.Waitlist;
import com.skyhigh.enums.WaitlistStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WaitlistRepository extends JpaRepository<Waitlist, Long> {

    List<Waitlist> findByFlightId(String flightId);

    List<Waitlist> findByPassengerId(String passengerId);

    List<Waitlist> findByStatus(WaitlistStatus status);

    @Query("SELECT w FROM Waitlist w WHERE w.flightId = :flightId AND w.seatNumber = :seatNumber AND w.status = :status ORDER BY w.position ASC")
    List<Waitlist> findByFlightIdAndSeatNumberAndStatus(
        @Param("flightId") String flightId,
        @Param("seatNumber") String seatNumber,
        @Param("status") WaitlistStatus status
    );

    @Query("SELECT w FROM Waitlist w WHERE w.flightId = :flightId AND w.seatNumber = :seatNumber AND w.status = com.skyhigh.enums.WaitlistStatus.WAITING ORDER BY w.position ASC")
    List<Waitlist> findWaitingEntriesBySeat(
        @Param("flightId") String flightId,
        @Param("seatNumber") String seatNumber
    );

    @Query("SELECT w FROM Waitlist w WHERE w.flightId = :flightId AND w.seatNumber = :seatNumber AND w.status = com.skyhigh.enums.WaitlistStatus.WAITING ORDER BY w.position ASC")
    Optional<Waitlist> findNextWaitingEntry(
        @Param("flightId") String flightId,
        @Param("seatNumber") String seatNumber
    );

    @Query("SELECT MAX(w.position) FROM Waitlist w WHERE w.flightId = :flightId AND w.seatNumber = :seatNumber")
    Optional<Integer> findMaxPositionBySeat(
        @Param("flightId") String flightId,
        @Param("seatNumber") String seatNumber
    );

    @Query("SELECT COUNT(w) FROM Waitlist w WHERE w.flightId = :flightId AND w.seatNumber = :seatNumber AND w.status = com.skyhigh.enums.WaitlistStatus.WAITING")
    long countWaitingEntriesBySeat(
        @Param("flightId") String flightId,
        @Param("seatNumber") String seatNumber
    );

    boolean existsByPassengerIdAndFlightIdAndSeatNumber(String passengerId, String flightId, String seatNumber);
}
