package com.skyhigh.service;

public interface AuditLogService {
    
    void logStateChange(String entityType, String entityId, String oldState, String newState, String userId);
}
