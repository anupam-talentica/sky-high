package com.skyhigh.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skyhigh.dto.notification.NotificationMessage;
import com.skyhigh.enums.NotificationType;
import com.skyhigh.exception.NotificationFailedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class NotificationServiceImpl implements NotificationService {

    private static final Logger logger = LoggerFactory.getLogger(NotificationServiceImpl.class);

    private final RedisTemplate<String, String> redisNotificationTemplate;
    private final ObjectMapper objectMapper;
    private final String queueName;

    public NotificationServiceImpl(RedisTemplate<String, String> redisNotificationTemplate,
                                   ObjectMapper objectMapper,
                                   @Value("${notifications.queueName:notifications:queue}") String queueName) {
        this.redisNotificationTemplate = redisNotificationTemplate;
        this.objectMapper = objectMapper;
        this.queueName = queueName;
    }

    @Override
    public void sendSeatAssignmentNotification(String passengerEmail, String passengerId,
                                               String flightId, String seatNumber) {
        String subject = String.format("Seat %s Available - Flight %s", seatNumber, flightId);
        String body = buildSeatAssignmentBody(passengerId, flightId, seatNumber);

        Map<String, String> metadata = new HashMap<>();
        metadata.put("passengerId", passengerId);
        metadata.put("flightId", flightId);
        metadata.put("seatNumber", seatNumber);
        metadata.put("template", "WAITLIST_SEAT_ASSIGNED");

        NotificationMessage message = NotificationMessage.newBuilder(NotificationType.WAITLIST_ASSIGNED, passengerEmail)
                .subject(subject)
                .body(body)
                .metadata(metadata)
                .build();

        enqueue(message);
    }

    @Override
    public void sendWaitlistExpirationNotification(String passengerEmail, String passengerId,
                                                   String flightId, String seatNumber) {
        String subject = String.format("Seat Assignment Expired - Flight %s", flightId);
        String body = buildExpirationBody(passengerId, flightId, seatNumber);

        Map<String, String> metadata = new HashMap<>();
        metadata.put("passengerId", passengerId);
        metadata.put("flightId", flightId);
        metadata.put("seatNumber", seatNumber);
        metadata.put("template", "WAITLIST_SEAT_EXPIRED");

        NotificationMessage message = NotificationMessage.newBuilder(NotificationType.WAITLIST_EXPIRED, passengerEmail)
                .subject(subject)
                .body(body)
                .metadata(metadata)
                .build();

        enqueue(message);
    }

    private void enqueue(NotificationMessage message) {
        try {
            String payload = objectMapper.writeValueAsString(message);
            ListOperations<String, String> ops = redisNotificationTemplate.opsForList();
            ops.rightPush(queueName, payload);
            logger.info("Enqueued notification message id={} type={} to queue={}",
                    message.getId(), message.getType(), queueName);
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize notification message: {}", e.getMessage(), e);
            throw new NotificationFailedException("Failed to serialize notification message", e);
        } catch (Exception e) {
            logger.error("Failed to enqueue notification message: {}", e.getMessage(), e);
            throw new NotificationFailedException("Failed to enqueue notification message", e);
        }
    }

    private String buildSeatAssignmentBody(String passengerId, String flightId, String seatNumber) {
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

    private String buildExpirationBody(String passengerId, String flightId, String seatNumber) {
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

