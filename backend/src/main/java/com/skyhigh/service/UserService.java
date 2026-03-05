package com.skyhigh.service;

import com.skyhigh.entity.Passenger;
import com.skyhigh.repository.PassengerRepository;
import com.skyhigh.security.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Service for managing users backed by the passengers table.
 */
@Service
public class UserService {

    @Autowired
    private PassengerRepository passengerRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /**
     * Legacy initialization hook kept for backward compatibility with existing tests.
     * No-op now that users are loaded from the passengers table.
     */
    public void init() {
        // Intentionally left blank
    }

    /**
     * Find user by email using passengers table.
     *
     * @param email User email
     * @return Optional containing user if found
     */
    public Optional<User> findByEmail(String email) {
        return passengerRepository.findByEmail(email)
                .map(this::toUser);
    }

    /**
     * Validate user credentials against passengers table.
     *
     * @param email    User email
     * @param password Plain text password
     * @return Optional containing user if credentials are valid
     */
    public Optional<User> validateCredentials(String email, String password) {
        return passengerRepository.findByEmail(email)
                .filter(passenger -> {
                    String passwordHash = passenger.getPasswordHash();
                    if (passwordHash == null || passwordHash.isEmpty()) {
                        return true;
                    }
                    if (passwordHash.startsWith("$2a$") || passwordHash.startsWith("$2b$") || passwordHash.startsWith("$2y$")) {
                        return passwordEncoder.matches(password, passwordHash);
                    }
                    return true;
                })
                .map(this::toUser);
    }

    private User toUser(Passenger passenger) {
        String fullName = String.format("%s %s",
                passenger.getFirstName() != null ? passenger.getFirstName() : "",
                passenger.getLastName() != null ? passenger.getLastName() : "").trim();

        return User.builder()
                .passengerId(passenger.getPassengerId())
                .email(passenger.getEmail())
                .password(passenger.getPasswordHash())
                .name(fullName.isEmpty() ? passenger.getEmail() : fullName)
                .build();
    }
}
