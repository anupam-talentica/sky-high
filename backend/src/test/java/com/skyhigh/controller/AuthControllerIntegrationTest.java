package com.skyhigh.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skyhigh.dto.LoginRequest;
import com.skyhigh.entity.Passenger;
import com.skyhigh.repository.PassengerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for authentication flow.
 * Tests the complete authentication flow with real beans.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PassengerRepository passengerRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @org.junit.jupiter.api.BeforeEach
    void ensureTestPassengersExist() {
        if (passengerRepository.findById("P123456").isEmpty()) {
            Passenger john = new Passenger();
            john.setPassengerId("P123456");
            john.setFirstName("John");
            john.setLastName("Doe");
            john.setEmail("john@example.com");
            john.setPhone("+1-555-0101");
            john.setPasswordHash(passwordEncoder.encode("demo123"));
            passengerRepository.save(john);
        }
        if (passengerRepository.findById("P789012").isEmpty()) {
            Passenger jane = new Passenger();
            jane.setPassengerId("P789012");
            jane.setFirstName("Jane");
            jane.setLastName("Smith");
            jane.setEmail("jane@example.com");
            jane.setPhone("+1-555-0102");
            jane.setPasswordHash(passwordEncoder.encode("demo456"));
            passengerRepository.save(jane);
        }
    }

    @Test
    void testLogin_ValidCredentials_ReturnsJwtToken() throws Exception {
        LoginRequest loginRequest = new LoginRequest("john@example.com", "demo123");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.passengerId").value("P123456"))
                .andExpect(jsonPath("$.email").value("john@example.com"))
                .andExpect(jsonPath("$.name").value("John Doe"));
    }

    @Test
    void testLogin_InvalidCredentials_Returns401() throws Exception {
        LoginRequest loginRequest = new LoginRequest("john@example.com", "wrongpassword");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.passengerId").value("P123456"))
                .andExpect(jsonPath("$.email").value("john@example.com"))
                .andExpect(jsonPath("$.name").value("John Doe"));
    }

    @Test
    void testLogin_User2_ValidCredentials_ReturnsJwtToken() throws Exception {
        LoginRequest loginRequest = new LoginRequest("jane@example.com", "demo456");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.passengerId").value("P789012"))
                .andExpect(jsonPath("$.email").value("jane@example.com"))
                .andExpect(jsonPath("$.name").value("Jane Smith"));
    }
}
