package com.skyhigh.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skyhigh.dto.LoginRequest;
import com.skyhigh.dto.SeatReservationRequestDTO;
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
class SeatControllerIntegrationTest {

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

        if (seatRepository.findByFlightIdAndSeatNumber("FL001", "1A").isEmpty()) {
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
    void getSeatMap_WithExistingFlight_ShouldReturnSeatMap() throws Exception {
        mockMvc.perform(get("/api/v1/flights/FL001/seat-map")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flightId").value("FL001"))
                .andExpect(jsonPath("$.totalSeats").isNumber());
    }

    @Test
    void reserveSeat_WhenSeatExists_ShouldCreateHold() throws Exception {
        SeatReservationRequestDTO request = new SeatReservationRequestDTO();
        request.setPassengerId("P123456");

        mockMvc.perform(post("/api/v1/flights/FL001/seats/1A/reserve")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.flightId").value("FL001"))
                .andExpect(jsonPath("$.seatNumber").value("1A"))
                .andExpect(jsonPath("$.heldBy").value("P123456"));
    }
}

