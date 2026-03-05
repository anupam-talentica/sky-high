package com.skyhigh.service;

import com.skyhigh.exception.NotificationFailedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class NotificationServiceImpl implements NotificationService {
    
    private static final Logger logger = LoggerFactory.getLogger(NotificationServiceImpl.class);
    
    @Override
    @Async
    public void sendSeatAssignmentNotification(String passengerEmail, String passengerId, 
                                              String flightId, String seatNumber) {
        try {
            logger.info("Sending seat assignment notification to {} for seat {} on flight {}", 
                passengerEmail, seatNumber, flightId);
            
            String subject = String.format("Seat %s Available - Flight %s", seatNumber, flightId);
            String body = buildSeatAssignmentEmail(passengerId, flightId, seatNumber);
            
            sendEmail(passengerEmail, subject, body);
            
            logger.info("Successfully sent seat assignment notification to {}", passengerEmail);
            
        } catch (Exception e) {
            logger.error("Failed to send seat assignment notification to {}: {}", 
                passengerEmail, e.getMessage(), e);
            throw new NotificationFailedException(
                "Failed to send seat assignment notification", e);
        }
    }
    
    @Override
    @Async
    public void sendWaitlistExpirationNotification(String passengerEmail, String passengerId,
                                                  String flightId, String seatNumber) {
        try {
            logger.info("Sending waitlist expiration notification to {} for seat {} on flight {}", 
                passengerEmail, seatNumber, flightId);
            
            String subject = String.format("Seat Assignment Expired - Flight %s", flightId);
            String body = buildExpirationEmail(passengerId, flightId, seatNumber);
            
            sendEmail(passengerEmail, subject, body);
            
            logger.info("Successfully sent expiration notification to {}", passengerEmail);
            
        } catch (Exception e) {
            logger.error("Failed to send expiration notification to {}: {}", 
                passengerEmail, e.getMessage(), e);
        }
    }
    
    private void sendEmail(String to, String subject, String body) {
        logger.info("Mock Email Service - Sending email");
        logger.info("To: {}", to);
        logger.info("Subject: {}", subject);
        logger.info("Body: {}", body);
        logger.info("---");
    }
    
    private String buildSeatAssignmentEmail(String passengerId, String flightId, String seatNumber) {
        return String.format("""
            Dear Passenger %s,
            
            Good news! A seat has become available for your waitlist request.
            
            Flight: %s
            Seat: %s
            
            Your seat has been reserved for the next 120 seconds. Please complete your check-in 
            immediately to confirm your seat assignment.
            
            If you do not complete check-in within 120 seconds, the seat will be assigned to 
            the next passenger on the waitlist.
            
            Thank you for choosing SkyHigh Airlines.
            
            Best regards,
            SkyHigh Airlines Team
            """, passengerId, flightId, seatNumber);
    }
    
    private String buildExpirationEmail(String passengerId, String flightId, String seatNumber) {
        return String.format("""
            Dear Passenger %s,
            
            Unfortunately, your seat assignment for the following flight has expired:
            
            Flight: %s
            Seat: %s
            
            The seat has been assigned to the next passenger on the waitlist as you did not 
            complete check-in within the 120-second time limit.
            
            You remain on the waitlist and will be notified if another seat becomes available.
            
            Thank you for your understanding.
            
            Best regards,
            SkyHigh Airlines Team
            """, passengerId, flightId, seatNumber);
    }
}
