package com.skyhigh.service;

import com.skyhigh.dto.SeatMapResponseDTO;
import com.skyhigh.dto.SeatReservationResponseDTO;
import com.skyhigh.entity.Seat;

public interface SeatService {
    
    SeatMapResponseDTO getAvailableSeats(String flightId);
    
    Seat getSeatById(Long seatId);
    
    Seat getSeatByFlightAndNumber(String flightId, String seatNumber);
    
    SeatReservationResponseDTO reserveSeat(String flightId, String seatNumber, String passengerId);
    
    SeatReservationResponseDTO reserveSeatForCheckIn(String flightId, String seatNumber, String passengerId);
    
    Seat releaseSeat(Long seatId);
    
    Seat confirmSeat(Long seatId, String passengerId);
    
    Seat cancelSeat(Long seatId);
    
    int releaseExpiredSeats();
}
