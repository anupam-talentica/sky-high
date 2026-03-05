package com.skyhigh.service;

import com.skyhigh.entity.AuditLog;
import com.skyhigh.repository.AuditLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceImplTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @InjectMocks
    private AuditLogServiceImpl auditLogService;

    @Test
    void logStateChange_WhenSuccessful_ShouldPersistAuditLogWithMappedStates() {
        String entityType = "SEAT";
        String entityId = "S1";
        String oldState = "AVAILABLE";
        String newState = "HELD";
        String userId = "user-123";

        auditLogService.logStateChange(entityType, entityId, oldState, newState, userId);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertNotNull(saved);
        assertEquals(entityType, saved.getEntityType());
        assertEquals(entityId, saved.getEntityId());
        assertEquals("STATE_CHANGE", saved.getAction());
        assertEquals(userId, saved.getUserId());
        assertNotNull(saved.getTimestamp());
        assertNotNull(saved.getOldState());
        assertNotNull(saved.getNewState());
        assertEquals(oldState, saved.getOldState().get("state"));
        assertEquals(newState, saved.getNewState().get("state"));
    }

    @Test
    void logStateChange_WhenRepositoryThrows_ShouldSwallowExceptionAndNotPropagate() {
        String entityType = "SEAT";
        String entityId = "S2";
        String oldState = "HELD";
        String newState = "AVAILABLE";
        String userId = "user-456";

        doThrow(new RuntimeException("DB failure"))
            .when(auditLogRepository)
            .save(org.mockito.ArgumentMatchers.any(AuditLog.class));

        auditLogService.logStateChange(entityType, entityId, oldState, newState, userId);

        verify(auditLogRepository).save(org.mockito.ArgumentMatchers.any(AuditLog.class));
    }
}

