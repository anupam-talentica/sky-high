package com.skyhigh.controller;

import com.skyhigh.dto.SeatMapResponseDTO;
import com.skyhigh.dto.SeatReservationRequestDTO;
import com.skyhigh.dto.SeatReservationResponseDTO;
import com.skyhigh.entity.Seat;
import com.skyhigh.service.SeatService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class SeatController {
    
    private static final Logger logger = LoggerFactory.getLogger(SeatController.class);
    
    private final SeatService seatService;
    
    public SeatController(SeatService seatService) {
        this.seatService = seatService;
    }
    
    @GetMapping("/flights/{flightId}/seat-map")
    public ResponseEntity<SeatMapResponseDTO> getSeatMap(@PathVariable String flightId) {
        logger.info("GET /api/v1/flights/{}/seat-map", flightId);
        SeatMapResponseDTO seatMap = seatService.getAvailableSeats(flightId);
        return ResponseEntity.ok(seatMap);
    }
    
    /**
     * Lightweight endpoint to support abuse/bot detection testing without returning full seat map.
     * Returns a minimal status payload for the given flight.
     */
    @GetMapping("/flights/{flightId}/seat-map/status")
    public ResponseEntity<String> getSeatMapStatus(@PathVariable String flightId) {
        logger.info("GET /api/v1/flights/{}/seat-map/status", flightId);
        // Intentionally lightweight: do not load full seat map; just acknowledge the request.
        return ResponseEntity.ok("OK");
    }
    
    @GetMapping("/seats/flight/{flightId}")
    public ResponseEntity<SeatMapResponseDTO> getSeatsByFlight(@PathVariable String flightId) {
        logger.info("GET /api/v1/seats/flight/{}", flightId);
        SeatMapResponseDTO seatMap = seatService.getAvailableSeats(flightId);
        return ResponseEntity.ok(seatMap);
    }
    
    @PostMapping("/flights/{flightId}/seats/{seatNumber}/reserve")
    public ResponseEntity<SeatReservationResponseDTO> reserveSeat(
            @PathVariable String flightId,
            @PathVariable String seatNumber,
            @Valid @RequestBody SeatReservationRequestDTO request) {
        
        logger.info("POST /api/v1/flights/{}/seats/{}/reserve by passenger {}", 
            flightId, seatNumber, request.getPassengerId());
        
        SeatReservationResponseDTO response = seatService.reserveSeat(
            flightId, 
            seatNumber, 
            request.getPassengerId()
        );
        
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    @PostMapping("/seats/flight/{flightId}/seat/{seatNumber}/reserve")
    public ResponseEntity<SeatReservationResponseDTO> reserveSeatAlt(
            @PathVariable String flightId,
            @PathVariable String seatNumber,
            @Valid @RequestBody SeatReservationRequestDTO request) {
        
        logger.info("POST /api/v1/seats/flight/{}/seat/{}/reserve by passenger {}", 
            flightId, seatNumber, request.getPassengerId());
        
        SeatReservationResponseDTO response = seatService.reserveSeat(
            flightId, 
            seatNumber, 
            request.getPassengerId()
        );
        
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    @PostMapping("/seats/{seatId}/release")
    public ResponseEntity<Seat> releaseSeat(@PathVariable Long seatId) {
        logger.info("POST /api/v1/seats/{}/release", seatId);
        Seat seat = seatService.releaseSeat(seatId);
        return ResponseEntity.ok(seat);
    }
    
    @PostMapping("/flights/{flightId}/seats/{seatNumber}/release")
    public ResponseEntity<Seat> releaseSeatByNumber(
            @PathVariable String flightId,
            @PathVariable String seatNumber) {
        logger.info("POST /api/v1/flights/{}/seats/{}/release", flightId, seatNumber);
        
        // Find seat by flight and seat number
        Seat seat = seatService.getSeatByFlightAndNumber(flightId, seatNumber);
        Seat releasedSeat = seatService.releaseSeat(seat.getSeatId());
        
        return ResponseEntity.ok(releasedSeat);
    }
    
    @PostMapping("/seats/{seatId}/confirm")
    public ResponseEntity<Seat> confirmSeat(
            @PathVariable Long seatId,
            @RequestParam String passengerId) {
        
        logger.info("POST /api/v1/seats/{}/confirm by passenger {}", seatId, passengerId);
        Seat seat = seatService.confirmSeat(seatId, passengerId);
        return ResponseEntity.ok(seat);
    }
    
    @PostMapping("/seats/{seatId}/cancel")
    public ResponseEntity<Seat> cancelSeat(@PathVariable Long seatId) {
        logger.info("POST /api/v1/seats/{}/cancel", seatId);
        Seat seat = seatService.cancelSeat(seatId);
        return ResponseEntity.ok(seat);
    }
}
