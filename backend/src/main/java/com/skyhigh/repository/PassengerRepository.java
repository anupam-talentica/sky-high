package com.skyhigh.repository;

import com.skyhigh.entity.Passenger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PassengerRepository extends JpaRepository<Passenger, String> {

    Optional<Passenger> findByEmail(String email);

    Optional<Passenger> findByPassportNumber(String passportNumber);

    boolean existsByEmail(String email);

    boolean existsByPassportNumber(String passportNumber);
}
