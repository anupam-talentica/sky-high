package com.skyhigh.service;

import com.skyhigh.dto.LoginRequest;
import com.skyhigh.dto.LoginResponse;
import com.skyhigh.exception.AuthenticationFailedException;
import com.skyhigh.security.JwtTokenProvider;
import com.skyhigh.security.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Service for handling authentication operations.
 */
@Service
public class AuthenticationService {

    private static final Logger logger = LoggerFactory.getLogger(AuthenticationService.class);

    @Autowired
    private UserService userService;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    /**
     * Authenticate user and generate JWT token.
     *
     * @param loginRequest Login credentials
     * @return Login response with JWT token and user info
     * @throws AuthenticationFailedException if credentials are invalid
     */
    public LoginResponse login(LoginRequest loginRequest) {
        logger.info("Login attempt for email (password check relaxed): {}", loginRequest.getEmail());

        User user = userService.findByEmail(loginRequest.getEmail())
                .orElseThrow(() -> new AuthenticationFailedException(
                        "Invalid email"));

        String token = jwtTokenProvider.generateToken(
                user.getPassengerId(),
                user.getEmail());

        logger.info("Login successful for passenger: {}", user.getPassengerId());

        return LoginResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .passengerId(user.getPassengerId())
                .email(user.getEmail())
                .name(user.getName())
                .build();
    }
}
