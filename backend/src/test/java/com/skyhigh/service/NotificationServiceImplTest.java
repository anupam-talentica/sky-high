package com.skyhigh.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.skyhigh.dto.notification.NotificationMessage;
import com.skyhigh.enums.NotificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationServiceImplTest {

    private RedisTemplate<String, String> redisTemplate;
    private ListOperations<String, String> listOperations;
    private ObjectMapper objectMapper;
    private NotificationServiceImpl notificationService;

    @BeforeEach
    void setUp() {
        //noinspection unchecked
        redisTemplate = (RedisTemplate<String, String>) mock(RedisTemplate.class);
        //noinspection unchecked
        listOperations = (ListOperations<String, String>) mock(ListOperations.class);
        when(redisTemplate.opsForList()).thenReturn(listOperations);

        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        notificationService = new NotificationServiceImpl(redisTemplate, objectMapper, "notifications:queue-test");
    }

    @Test
    void sendSeatAssignmentNotification_ShouldEnqueueExpectedPayload() throws Exception {
        notificationService.sendSeatAssignmentNotification(
                "passenger@example.com",
                "P123",
                "SK123",
                "12A"
        );

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(listOperations).rightPush(eq("notifications:queue-test"), payloadCaptor.capture());

        String payload = payloadCaptor.getValue();
        assertNotNull(payload);

        NotificationMessage message = objectMapper.readValue(payload, NotificationMessage.class);
        assertEquals(NotificationType.WAITLIST_ASSIGNED, message.getType());
        assertEquals("passenger@example.com", message.getRecipient());
        assertEquals("P123", message.getMetadata().get("passengerId"));
        assertEquals("SK123", message.getMetadata().get("flightId"));
        assertEquals("12A", message.getMetadata().get("seatNumber"));
        assertEquals("WAITLIST_SEAT_ASSIGNED", message.getMetadata().get("template"));
    }

    @Test
    void sendWaitlistExpirationNotification_ShouldEnqueueExpectedPayload() throws Exception {
        notificationService.sendWaitlistExpirationNotification(
                "passenger@example.com",
                "P999",
                "SK999",
                "22B"
        );

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(listOperations).rightPush(eq("notifications:queue-test"), payloadCaptor.capture());

        String payload = payloadCaptor.getValue();
        assertNotNull(payload);

        NotificationMessage message = objectMapper.readValue(payload, NotificationMessage.class);
        assertEquals(NotificationType.WAITLIST_EXPIRED, message.getType());
        assertEquals("passenger@example.com", message.getRecipient());
        Map<String, String> metadata = message.getMetadata();
        assertEquals("P999", metadata.get("passengerId"));
        assertEquals("SK999", metadata.get("flightId"));
        assertEquals("22B", metadata.get("seatNumber"));
        assertEquals("WAITLIST_SEAT_EXPIRED", metadata.get("template"));
    }
}
