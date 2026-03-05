package com.skyhigh.service;

import com.skyhigh.dto.*;
import com.skyhigh.entity.CheckIn;

import java.util.List;

public interface CheckInService {

    CheckInResponseDTO startCheckIn(CheckInRequestDTO request);

    CheckInResponseDTO selectSeat(String checkInId, SeatReservationRequestDTO request);

    BaggageResponseDTO addBaggage(String checkInId, BaggageDetailsDTO baggageDetails);

    List<BaggageResponseDTO> getBaggageForCheckIn(String checkInId);

    void deleteBaggage(String checkInId, Long baggageId);

    PaymentResponseDTO processPayment(String checkInId, PaymentRequestDTO paymentRequest);

    CheckInResponseDTO confirmCheckIn(String checkInId);

    CheckInResponseDTO cancelCheckIn(String checkInId);

    CheckInResponseDTO getCheckInDetails(String checkInId);

    /**
     * Returns a lightweight list of check-ins for the given passenger, used by the frontend
     * to determine existing/completed check-ins per flight.
     */
    List<PassengerCheckInSummaryDTO> getCheckInsForPassenger(String passengerId);

    CheckIn getCheckInById(String checkInId);
}
