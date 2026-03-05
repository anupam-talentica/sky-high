package com.skyhigh.repository;

import com.skyhigh.entity.CheckIn;
import com.skyhigh.enums.CheckInStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CheckInRepository extends JpaRepository<CheckIn, String> {

    List<CheckIn> findByPassengerId(String passengerId);

    List<CheckIn> findByFlightId(String flightId);

    List<CheckIn> findByPassengerIdAndFlightId(String passengerId, String flightId);

    Optional<CheckIn> findFirstByPassengerIdAndFlightIdAndStatusNotOrderByCreatedAtDesc(
        String passengerId, 
        String flightId, 
        CheckInStatus status
    );

    List<CheckIn> findByStatus(CheckInStatus status);

    List<CheckIn> findByPassengerIdAndStatus(String passengerId, CheckInStatus status);

    @Query("SELECT c FROM CheckIn c WHERE c.flightId = :flightId AND c.status = :status")
    List<CheckIn> findByFlightIdAndStatus(
        @Param("flightId") String flightId,
        @Param("status") CheckInStatus status
    );

    @Query("SELECT c FROM CheckIn c WHERE c.checkInTime BETWEEN :startTime AND :endTime")
    List<CheckIn> findCheckInsByTimeRange(
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime
    );

    @Query("SELECT COUNT(c) FROM CheckIn c WHERE c.flightId = :flightId AND c.status = 'COMPLETED'")
    long countCompletedCheckInsByFlight(@Param("flightId") String flightId);

    boolean existsByPassengerIdAndFlightId(String passengerId, String flightId);

    java.util.Optional<CheckIn> findFirstByPassengerIdAndFlightIdOrderByCreatedAtDesc(
            String passengerId,
            String flightId
    );
}
