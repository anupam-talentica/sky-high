package com.skyhigh.dto.notification;

import com.skyhigh.enums.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationMessage {

    private String id;
    private NotificationType type;
    private String recipient;
    private String subject;
    private String body;
    private Map<String, String> metadata;
    private LocalDateTime createdAt;

    public static NotificationMessageBuilder newBuilder(NotificationType type, String recipient) {
        return NotificationMessage.builder()
                .id(UUID.randomUUID().toString())
                .type(type)
                .recipient(recipient)
                .createdAt(LocalDateTime.now());
    }
}

