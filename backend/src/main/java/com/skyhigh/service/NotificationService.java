package com.skyhigh.service;

public interface NotificationService {
    
    /**
     * Send seat assignment notification to passenger.
     *
     * @param passengerEmail Email address of the passenger
     * @param passengerId Passenger ID
     * @param flightId Flight ID
     * @param seatNumber Assigned seat number
     */
    void sendSeatAssignmentNotification(String passengerEmail, String passengerId, 
                                       String flightId, String seatNumber);
    
    /**
     * Send waitlist expiration notification to passenger.
     *
     * @param passengerEmail Email address of the passenger
     * @param passengerId Passenger ID
     * @param flightId Flight ID
     * @param seatNumber Seat number
     */
    void sendWaitlistExpirationNotification(String passengerEmail, String passengerId,
                                           String flightId, String seatNumber);
}
