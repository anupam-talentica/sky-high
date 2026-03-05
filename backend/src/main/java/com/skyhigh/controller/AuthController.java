package com.skyhigh.controller;

import com.skyhigh.dto.LoginRequest;
import com.skyhigh.dto.LoginResponse;
import com.skyhigh.service.AuthenticationService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for authentication endpoints.
 * Handles user login and JWT token generation.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    @Autowired
    private AuthenticationService authenticationService;

    /**
     * Login endpoint.
     * Validates credentials and returns JWT token.
     *
     * @param loginRequest Login credentials (email and password)
     * @return Login response with JWT token and user info
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest loginRequest) {
        logger.info("POST /api/v1/auth/login - Login request received");
        
        LoginResponse response = authenticationService.login(loginRequest);
        
        return ResponseEntity.ok(response);
    }

    /**
     * Health check endpoint for authentication service.
     *
     * @return Simple status message
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Authentication service is running");
    }
}
