package com.skyhigh.controller;

import com.skyhigh.dto.WaitlistJoinRequestDTO;
import com.skyhigh.dto.WaitlistPositionDTO;
import com.skyhigh.dto.WaitlistResponseDTO;
import com.skyhigh.service.WaitlistService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class WaitlistController {
    
    private static final Logger logger = LoggerFactory.getLogger(WaitlistController.class);
    
    private final WaitlistService waitlistService;
    
    public WaitlistController(WaitlistService waitlistService) {
        this.waitlistService = waitlistService;
    }
    
    /**
     * Join waitlist for a specific seat.
     * POST /api/v1/flights/{flightId}/seats/{seatNumber}/waitlist
     */
    @PostMapping("/flights/{flightId}/seats/{seatNumber}/waitlist")
    public ResponseEntity<WaitlistResponseDTO> joinWaitlist(
            @PathVariable String flightId,
            @PathVariable String seatNumber,
            @Valid @RequestBody WaitlistJoinRequestDTO request) {
        
        logger.info("Request to join waitlist for seat {} on flight {} by passenger {}", 
            seatNumber, flightId, request.getPassengerId());
        System.out.println("Request to join waitlist for seat " + seatNumber + " on flight " + flightId + " by passenger " + request.getPassengerId());
        WaitlistResponseDTO response = waitlistService.joinWaitlist(
            request.getPassengerId(), flightId, seatNumber);
        
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    /**
     * Leave waitlist.
     * DELETE /api/v1/waitlist/{waitlistId}
     */
    @DeleteMapping("/waitlist/{waitlistId}")
    public ResponseEntity<Void> leaveWaitlist(@PathVariable Long waitlistId) {
        
        logger.info("Request to leave waitlist entry: {}", waitlistId);
        
        waitlistService.leaveWaitlist(waitlistId);
        
        return ResponseEntity.noContent().build();
    }
    
    /**
     * Get waitlist position.
     * GET /api/v1/waitlist/{waitlistId}/position
     */
    @GetMapping("/waitlist/{waitlistId}/position")
    public ResponseEntity<WaitlistPositionDTO> getWaitlistPosition(@PathVariable Long waitlistId) {
        
        logger.info("Request to get waitlist position for entry: {}", waitlistId);
        
        WaitlistPositionDTO position = waitlistService.getWaitlistPosition(waitlistId);
        
        return ResponseEntity.ok(position);
    }
    
    /**
     * Get all waitlist entries for a passenger.
     * GET /api/v1/passengers/{passengerId}/waitlist
     */
    @GetMapping("/passengers/{passengerId}/waitlist")
    public ResponseEntity<List<WaitlistResponseDTO>> getPassengerWaitlist(
            @PathVariable String passengerId) {
        
        logger.info("Request to get waitlist entries for passenger: {}", passengerId);
        
        List<WaitlistResponseDTO> waitlistEntries = waitlistService.getPassengerWaitlist(passengerId);
        
        return ResponseEntity.ok(waitlistEntries);
    }
}
