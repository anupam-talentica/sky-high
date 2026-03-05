package com.skyhigh.service;

import com.skyhigh.dto.WaitlistPositionDTO;
import com.skyhigh.dto.WaitlistResponseDTO;
import com.skyhigh.entity.Waitlist;

import java.util.List;

public interface WaitlistService {
    
    /**
     * Join waitlist for a specific seat.
     *
     * @param passengerId Passenger ID
     * @param flightId Flight ID
     * @param seatNumber Seat number
     * @return Waitlist entry details
     */
    WaitlistResponseDTO joinWaitlist(String passengerId, String flightId, String seatNumber);
    
    /**
     * Leave waitlist.
     *
     * @param waitlistId Waitlist entry ID
     */
    void leaveWaitlist(Long waitlistId);
    
    /**
     * Process waitlist when a seat becomes available.
     * Assigns seat to next passenger in FIFO order.
     *
     * @param flightId Flight ID
     * @param seatNumber Seat number that became available
     */
    void processWaitlist(String flightId, String seatNumber);
    
    /**
     * Get waitlist position for a specific entry.
     *
     * @param waitlistId Waitlist entry ID
     * @return Position information
     */
    WaitlistPositionDTO getWaitlistPosition(Long waitlistId);
    
    /**
     * Get all waitlist entries for a passenger.
     *
     * @param passengerId Passenger ID
     * @return List of waitlist entries
     */
    List<WaitlistResponseDTO> getPassengerWaitlist(String passengerId);
    
    /**
     * Expire waitlist assignment if passenger doesn't confirm within time limit.
     *
     * @param waitlistId Waitlist entry ID
     */
    void expireWaitlistAssignment(Long waitlistId);
}
