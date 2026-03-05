package com.skyhigh.repository;

import com.skyhigh.entity.Flight;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface FlightRepository extends JpaRepository<Flight, String> {

    Optional<Flight> findByFlightNumber(String flightNumber);

    List<Flight> findByStatus(String status);

    List<Flight> findByDepartureAirportAndArrivalAirport(String departureAirport, String arrivalAirport);

    @Query("SELECT f FROM Flight f WHERE f.departureTime BETWEEN :startTime AND :endTime")
    List<Flight> findFlightsByDepartureTimeRange(
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime
    );

    @Query("SELECT f FROM Flight f WHERE f.departureTime >= :currentTime AND f.status = :status ORDER BY f.departureTime ASC")
    List<Flight> findUpcomingFlightsByStatus(
        @Param("currentTime") LocalDateTime currentTime,
        @Param("status") String status
    );
}
