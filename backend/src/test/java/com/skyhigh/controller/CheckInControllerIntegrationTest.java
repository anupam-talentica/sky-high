package com.skyhigh.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skyhigh.dto.BaggageDetailsDTO;
import com.skyhigh.dto.CheckInRequestDTO;
import com.skyhigh.dto.LoginRequest;
import com.skyhigh.dto.PaymentRequestDTO;
import com.skyhigh.entity.Passenger;
import com.skyhigh.enums.BaggageType;
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

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CheckInControllerIntegrationTest {

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

    @Autowired
    private com.skyhigh.repository.ReservationRepository reservationRepository;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

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
            passengerRepository.saveAndFlush(john);
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
            flightRepository.saveAndFlush(flight);
        }

        if (seatRepository.findByFlightIdAndSeatNumber("FL001", "1B").isEmpty()) {
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
            seatRepository.flush();
        }

        if (!reservationRepository.existsByPassengerIdAndFlightIdAndStatus("P123456", "FL001", "ACTIVE")) {
            com.skyhigh.entity.Reservation reservation = new com.skyhigh.entity.Reservation();
            reservation.setPassengerId("P123456");
            reservation.setFlightId("FL001");
            reservation.setBookingReference("BK123456");
            reservation.setStatus("ACTIVE");
            reservationRepository.saveAndFlush(reservation);
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

    //@Test
    void completeCheckInWorkflow_ForPassengerWithReservation_ShouldSucceed() throws Exception {
        CheckInRequestDTO checkInRequest = new CheckInRequestDTO();
        checkInRequest.setPassengerId("P123456");
        checkInRequest.setFlightId("FL001");
        checkInRequest.setSeatNumber("1B");

        String checkInResponse = mockMvc.perform(post("/api/v1/check-ins/initiate")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(checkInRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.checkInId").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String checkInId = objectMapper.readTree(checkInResponse).get("checkInId").asText();

        com.skyhigh.dto.SeatReservationRequestDTO seatRequest = new com.skyhigh.dto.SeatReservationRequestDTO();
        seatRequest.setPassengerId("P123456");
        seatRequest.setSeatNumber("1B");

        mockMvc.perform(post("/api/v1/check-ins/{checkInId}/select-seat", checkInId)
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(seatRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seatNumber").value("1B"));

        BaggageDetailsDTO baggageRequest = BaggageDetailsDTO.builder()
                .weightKg(new BigDecimal("20.00"))
                .baggageType(BaggageType.CHECKED)
                .build();

        mockMvc.perform(post("/api/v1/check-ins/{checkInId}/baggage", checkInId)
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(baggageRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baggageId").exists());

        PaymentRequestDTO paymentRequest = new PaymentRequestDTO();
        paymentRequest.setAmount(BigDecimal.ZERO);
        paymentRequest.setPaymentMethod("CARD");

        mockMvc.perform(post("/api/v1/check-ins/{checkInId}/payment", checkInId)
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(paymentRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));

        mockMvc.perform(post("/api/v1/check-ins/{checkInId}/confirm", checkInId)
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        mockMvc.perform(get("/api/v1/check-ins/{checkInId}", checkInId)
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkInId").value(checkInId))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }
}

