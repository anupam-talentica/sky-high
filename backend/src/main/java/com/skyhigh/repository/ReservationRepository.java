package com.skyhigh.repository;

import com.skyhigh.entity.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    boolean existsByPassengerIdAndFlightIdAndStatus(String passengerId, String flightId, String status);

    java.util.List<Reservation> findByPassengerIdAndStatus(String passengerId, String status);
}

