package com.skyhigh.service;

import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationServiceImplTest {

    private final NotificationServiceImpl notificationService = new NotificationServiceImpl();

    @Test
    void sendSeatAssignmentNotification_ShouldNotThrowException() {
        assertDoesNotThrow(() ->
                notificationService.sendSeatAssignmentNotification(
                        "passenger@example.com",
                        "P123",
                        "SK123",
                        "12A"
                )
        );
    }

    @Test
    void sendWaitlistExpirationNotification_ShouldNotThrowException() {
        assertDoesNotThrow(() ->
                notificationService.sendWaitlistExpirationNotification(
                        "passenger@example.com",
                        "P123",
                        "SK123",
                        "12A"
                )
        );
    }

    @Test
    void buildSeatAssignmentEmail_ShouldContainAllRelevantDetails() throws Exception {
        String passengerId = "P123";
        String flightId = "SK123";
        String seatNumber = "12A";

        String emailBody = invokePrivateEmailBuilder(
                "buildSeatAssignmentEmail",
                passengerId,
                flightId,
                seatNumber
        );

        assertFalse(emailBody.isBlank());
        assertTrue(emailBody.contains(passengerId));
        assertTrue(emailBody.contains(flightId));
        assertTrue(emailBody.contains(seatNumber));
        assertTrue(emailBody.contains("120 seconds"));
    }

    @Test
    void buildExpirationEmail_ShouldContainAllRelevantDetails() throws Exception {
        String passengerId = "P999";
        String flightId = "SK999";
        String seatNumber = "22B";

        String emailBody = invokePrivateEmailBuilder(
                "buildExpirationEmail",
                passengerId,
                flightId,
                seatNumber
        );

        assertFalse(emailBody.isBlank());
        assertTrue(emailBody.contains(passengerId));
        assertTrue(emailBody.contains(flightId));
        assertTrue(emailBody.contains(seatNumber));
        assertTrue(emailBody.contains("expired"));
    }

    /**
     * Helper method to invoke private email builder methods using reflection.
     */
    private String invokePrivateEmailBuilder(String methodName,
                                             String passengerId,
                                             String flightId,
                                             String seatNumber) throws Exception {
        try {
            Method method = NotificationServiceImpl.class.getDeclaredMethod(
                    methodName,
                    String.class,
                    String.class,
                    String.class
            );
            method.setAccessible(true);
            Object result = method.invoke(notificationService, passengerId, flightId, seatNumber);
            return (String) result;
        } catch (InvocationTargetException e) {
            throw e;
        }
    }
}

