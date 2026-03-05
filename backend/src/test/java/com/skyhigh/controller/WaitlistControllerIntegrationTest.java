package com.skyhigh.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skyhigh.dto.LoginRequest;
import com.skyhigh.dto.WaitlistJoinRequestDTO;
import com.skyhigh.entity.Passenger;
import com.skyhigh.repository.PassengerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WaitlistControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PassengerRepository passengerRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private com.skyhigh.repository.FlightRepository flightRepository;

    @Autowired
    private com.skyhigh.repository.SeatRepository seatRepository;

    private String jwtToken;

    @BeforeEach
    void setUp() throws Exception {
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

        if (flightRepository.findById("FL001").isEmpty()) {
            com.skyhigh.entity.Flight flight = new com.skyhigh.entity.Flight();
            flight.setFlightId("FL001");
            flight.setFlightNumber("SK1234");
            flight.setDepartureAirport("JFK");
            flight.setArrivalAirport("LAX");
            flight.setDepartureTime(java.time.LocalDateTime.now().plusDays(2));
            flight.setArrivalTime(java.time.LocalDateTime.now().plusDays(2).plusHours(6));
            flight.setAircraftType("Boeing 737-800");
            flight.setTotalSeats(189);
            flight.setStatus("scheduled");
            flightRepository.save(flight);
        }

        if (seatRepository.findByFlightIdAndSeatNumber("FL001", "1C").isEmpty()) {
            for (int row = 1; row <= 3; row++) {
                for (String letter : new String[]{"A", "B", "C", "D"}) {
                    com.skyhigh.entity.Seat seat = new com.skyhigh.entity.Seat();
                    seat.setFlightId("FL001");
                    seat.setSeatNumber(row + letter);
                    seat.setSeatType(letter.equals("A") || letter.equals("D") ? 
                        com.skyhigh.enums.SeatType.WINDOW : com.skyhigh.enums.SeatType.AISLE);
                    seat.setState(com.skyhigh.enums.SeatState.AVAILABLE);
                    seat.setCreatedAt(java.time.LocalDateTime.now());
                    seat.setUpdatedAt(java.time.LocalDateTime.now());
                    seatRepository.save(seat);
                }
            }
        }

        LoginRequest loginRequest = new LoginRequest("john@example.com", "demo123");
        String loginResponse = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        jwtToken = objectMapper.readTree(loginResponse).get("token").asText();
    }

    @Test
    void joinWaitlist_AndFetchPosition_ForExistingFlightAndSeat_ShouldSucceed() throws Exception {
        com.skyhigh.dto.SeatReservationRequestDTO reserveRequest = new com.skyhigh.dto.SeatReservationRequestDTO();
        reserveRequest.setPassengerId("P789012");
        
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
        
        mockMvc.perform(post("/api/v1/flights/FL001/seats/1C/reserve")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reserveRequest)))
                .andExpect(status().isCreated());
        
        WaitlistJoinRequestDTO request = new WaitlistJoinRequestDTO();
        request.setPassengerId("P123456");

        String responseBody = mockMvc.perform(post("/api/v1/flights/FL001/seats/1C/waitlist")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.waitlistId").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();

        long waitlistId = objectMapper.readTree(responseBody).get("waitlistId").asLong();

        mockMvc.perform(get("/api/v1/waitlist/{waitlistId}/position", waitlistId)
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.waitlistId").value(waitlistId))
                .andExpect(jsonPath("$.seatNumber").value("1C"));
    }
}

