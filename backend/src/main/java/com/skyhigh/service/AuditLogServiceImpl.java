package com.skyhigh.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skyhigh.entity.AuditLog;
import com.skyhigh.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
public class AuditLogServiceImpl implements AuditLogService {
    
    private static final Logger logger = LoggerFactory.getLogger(AuditLogServiceImpl.class);
    
    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;
    
    public AuditLogServiceImpl(AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }
    
    @Override
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logStateChange(String entityType, String entityId, String oldState, String newState, String userId) {
        try {
            Map<String, Object> oldStateMap = new HashMap<>();
            oldStateMap.put("state", oldState);
            
            Map<String, Object> newStateMap = new HashMap<>();
            newStateMap.put("state", newState);
            
            AuditLog auditLog = new AuditLog();
            auditLog.setEntityType(entityType);
            auditLog.setEntityId(entityId);
            auditLog.setAction("STATE_CHANGE");
            auditLog.setOldState(oldStateMap);
            auditLog.setNewState(newStateMap);
            auditLog.setUserId(userId);
            auditLog.setTimestamp(LocalDateTime.now());
            
            auditLogRepository.save(auditLog);
            
            logger.debug("Audit log created: {} {} transitioned from {} to {} by {}", 
                entityType, entityId, oldState, newState, userId);
            
        } catch (Exception e) {
            logger.error("Failed to create audit log for {} {}: {}", 
                entityType, entityId, e.getMessage());
        }
    }
}
