package com.skyhigh.service;

import com.skyhigh.dto.FlightDTO;
import com.skyhigh.dto.FlightListResponseDTO;
import com.skyhigh.entity.Flight;
import com.skyhigh.entity.Seat;
import com.skyhigh.enums.SeatState;
import com.skyhigh.exception.SeatNotFoundException;
import com.skyhigh.repository.FlightRepository;
import com.skyhigh.repository.ReservationRepository;
import com.skyhigh.repository.SeatRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FlightServiceImplTest {

    @Mock
    private FlightRepository flightRepository;

    @Mock
    private SeatRepository seatRepository;

    @Mock
    private ReservationRepository reservationRepository;

    @InjectMocks
    private FlightServiceImpl flightService;

    private Flight flightOne;
    private Flight flightTwo;

    @BeforeEach
    void setUp() {
        flightOne = new Flight(
            "F1",
            "SK100",
            "SFO",
            "JFK",
            LocalDateTime.now().plusHours(2),
            LocalDateTime.now().plusHours(7),
            "A320",
            180,
            "scheduled",
            LocalDateTime.now(),
            LocalDateTime.now()
        );

        flightTwo = new Flight(
            "F2",
            "SK200",
            "SFO",
            "LAX",
            LocalDateTime.now().plusHours(3),
            LocalDateTime.now().plusHours(4),
            "B737",
            160,
            "scheduled",
            LocalDateTime.now(),
            LocalDateTime.now()
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getAllFlights_WhenActiveReservationsExist_ShouldFilterAndReturnDtos() {
        setAuthenticatedPassenger("P1");

        when(flightRepository.findUpcomingFlightsByStatus(any(LocalDateTime.class), eq("scheduled")))
            .thenReturn(List.of(flightOne, flightTwo));
        when(reservationRepository.existsByPassengerIdAndFlightIdAndStatus("P1", "F1", "ACTIVE"))
            .thenReturn(true);
        when(reservationRepository.existsByPassengerIdAndFlightIdAndStatus("P1", "F2", "ACTIVE"))
            .thenReturn(false);
        when(seatRepository.findByFlightIdAndState("F1", SeatState.AVAILABLE))
            .thenReturn(List.of(new Seat(), new Seat(), new Seat()));

        FlightListResponseDTO result = flightService.getAllFlights();

        assertNotNull(result);
        assertEquals(1, result.getTotal());
        assertEquals(1, result.getFlights().size());

        FlightDTO dto = result.getFlights().get(0);
        assertEquals("F1", dto.getFlightId());
        assertEquals("SK100", dto.getFlightNumber());
        assertEquals("SFO", dto.getOrigin());
        assertEquals("JFK", dto.getDestination());
        assertEquals("scheduled", dto.getStatus());
        assertEquals("A320", dto.getAircraftType());
        assertEquals(180, dto.getTotalSeats());
        assertEquals(3, dto.getAvailableSeats());

        verify(flightRepository).findUpcomingFlightsByStatus(any(LocalDateTime.class), eq("scheduled"));
        verify(reservationRepository).existsByPassengerIdAndFlightIdAndStatus("P1", "F1", "ACTIVE");
        verify(reservationRepository).existsByPassengerIdAndFlightIdAndStatus("P1", "F2", "ACTIVE");
        verify(seatRepository).findByFlightIdAndState("F1", SeatState.AVAILABLE);
        verify(seatRepository, never()).findByFlightIdAndState("F2", SeatState.AVAILABLE);
    }

    @Test
    void getFlightById_WhenFound_ShouldReturnDto() {
        when(flightRepository.findById("F1")).thenReturn(Optional.of(flightOne));
        when(seatRepository.findByFlightIdAndState("F1", SeatState.AVAILABLE))
            .thenReturn(List.of(new Seat(), new Seat()));

        FlightDTO result = flightService.getFlightById("F1");

        assertNotNull(result);
        assertEquals("F1", result.getFlightId());
        assertEquals("SK100", result.getFlightNumber());
        assertEquals("SFO", result.getOrigin());
        assertEquals("JFK", result.getDestination());
        assertEquals(2, result.getAvailableSeats());
    }

    @Test
    void getFlightById_WhenMissing_ShouldThrowSeatNotFound() {
        when(flightRepository.findById("F404")).thenReturn(Optional.empty());

        assertThrows(SeatNotFoundException.class, () -> flightService.getFlightById("F404"));

        verify(seatRepository, never()).findByFlightIdAndState(anyString(), any(SeatState.class));
    }

    @Test
    void searchFlights_WithOriginAndDestination_ShouldUseAirportSearch() {
        setAuthenticatedPassenger("P2");

        when(flightRepository.findByDepartureAirportAndArrivalAirport("SFO", "JFK"))
            .thenReturn(List.of(flightOne));
        when(reservationRepository.existsByPassengerIdAndFlightIdAndStatus("P2", "F1", "ACTIVE"))
            .thenReturn(true);
        when(seatRepository.findByFlightIdAndState("F1", SeatState.AVAILABLE))
            .thenReturn(List.of(new Seat()));

        FlightListResponseDTO result = flightService.searchFlights("SFO", "JFK");

        assertEquals(1, result.getTotal());
        assertEquals(1, result.getFlights().size());

        verify(flightRepository).findByDepartureAirportAndArrivalAirport("SFO", "JFK");
        verify(flightRepository, never())
            .findUpcomingFlightsByStatus(any(LocalDateTime.class), anyString());
    }

    @Test
    void searchFlights_WhenOriginMissing_ShouldUseUpcomingSearch() {
        setAuthenticatedPassenger("P3");

        when(flightRepository.findUpcomingFlightsByStatus(any(LocalDateTime.class), eq("scheduled")))
            .thenReturn(List.of(flightTwo));
        when(reservationRepository.existsByPassengerIdAndFlightIdAndStatus("P3", "F2", "ACTIVE"))
            .thenReturn(false);

        FlightListResponseDTO result = flightService.searchFlights(null, "JFK");

        assertNotNull(result);
        assertEquals(0, result.getTotal());
        assertTrue(result.getFlights().isEmpty());

        verify(flightRepository).findUpcomingFlightsByStatus(any(LocalDateTime.class), eq("scheduled"));
        verify(flightRepository, never()).findByDepartureAirportAndArrivalAirport(anyString(), anyString());
        verify(seatRepository, never()).findByFlightIdAndState(anyString(), any(SeatState.class));
    }

    @Test
    void getAllFlights_WhenNoAuthentication_ShouldThrowIllegalState() {
        SecurityContextHolder.clearContext();

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> flightService.getAllFlights());

        assertTrue(ex.getMessage().contains("No authenticated passenger"));
        verifyNoInteractions(flightRepository, seatRepository, reservationRepository);
    }

    @Test
    void searchFlights_WhenPrincipalMissing_ShouldThrowIllegalState() {
        SecurityContext securityContext = mock(SecurityContext.class);
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(null);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> flightService.searchFlights("SFO", "JFK"));

        assertTrue(ex.getMessage().contains("No authenticated passenger"));
        verifyNoInteractions(flightRepository, seatRepository, reservationRepository);
    }

    private void setAuthenticatedPassenger(String passengerId) {
        SecurityContext securityContext = mock(SecurityContext.class);
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(passengerId);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);
    }
}
