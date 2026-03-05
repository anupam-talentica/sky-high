package com.skyhigh.service;

import com.skyhigh.dto.FlightDTO;
import com.skyhigh.dto.FlightListResponseDTO;
import com.skyhigh.entity.Flight;
import com.skyhigh.enums.SeatState;
import com.skyhigh.exception.SeatNotFoundException;
import com.skyhigh.repository.FlightRepository;
import com.skyhigh.repository.ReservationRepository;
import com.skyhigh.repository.SeatRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class FlightServiceImpl implements FlightService {

    private final FlightRepository flightRepository;
    private final SeatRepository seatRepository;
    private final ReservationRepository reservationRepository;

    public FlightServiceImpl(FlightRepository flightRepository,
                             SeatRepository seatRepository,
                             ReservationRepository reservationRepository) {
        this.flightRepository = flightRepository;
        this.seatRepository = seatRepository;
        this.reservationRepository = reservationRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public FlightListResponseDTO getAllFlights() {
        String passengerId = getCurrentPassengerId();

        List<Flight> flights = flightRepository.findUpcomingFlightsByStatus(
                LocalDateTime.now(),
                "scheduled"
        ).stream()
                .filter(flight -> reservationRepository.existsByPassengerIdAndFlightIdAndStatus(
                        passengerId,
                        flight.getFlightId(),
                        "ACTIVE"
                ))
                .collect(Collectors.toList());

        List<FlightDTO> flightDTOs = flights.stream()
            .map(this::convertToDTO)
            .collect(Collectors.toList());

        return new FlightListResponseDTO(flightDTOs, flightDTOs.size());
    }

    @Override
    @Transactional(readOnly = true)
    public FlightDTO getFlightById(String flightId) {
        Flight flight = flightRepository.findById(flightId)
            .orElseThrow(() -> new SeatNotFoundException("Flight not found: " + flightId));
        
        return convertToDTO(flight);
    }

    @Override
    @Transactional(readOnly = true)
    public FlightListResponseDTO searchFlights(String origin, String destination) {
        String passengerId = getCurrentPassengerId();
        List<Flight> flights;
        
        if (origin != null && destination != null) {
            flights = flightRepository.findByDepartureAirportAndArrivalAirport(origin, destination);
        } else {
            flights = flightRepository.findUpcomingFlightsByStatus(
                LocalDateTime.now(),
                "scheduled"
            );
        }

        flights = flights.stream()
                .filter(flight -> reservationRepository.existsByPassengerIdAndFlightIdAndStatus(
                        passengerId,
                        flight.getFlightId(),
                        "ACTIVE"
                ))
                .collect(Collectors.toList());

        List<FlightDTO> flightDTOs = flights.stream()
            .map(this::convertToDTO)
            .collect(Collectors.toList());

        return new FlightListResponseDTO(flightDTOs, flightDTOs.size());
    }

    private FlightDTO convertToDTO(Flight flight) {
        int availableSeats = seatRepository.findByFlightIdAndState(
            flight.getFlightId(),
            SeatState.AVAILABLE
        ).size();

        return new FlightDTO(
            flight.getFlightId(),
            flight.getFlightNumber(),
            flight.getDepartureAirport(),
            flight.getArrivalAirport(),
            flight.getDepartureTime(),
            flight.getArrivalTime(),
            flight.getStatus(),
            flight.getAircraftType(),
            flight.getTotalSeats(),
            availableSeats
        );
    }

    private String getCurrentPassengerId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new IllegalStateException("No authenticated passenger found in security context");
        }
        return (String) authentication.getPrincipal();
    }
}
