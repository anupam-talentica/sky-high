-- Create audit_logs table for tracking state changes
CREATE TABLE audit_logs (
    log_id BIGSERIAL PRIMARY KEY,
    entity_type VARCHAR(50) NOT NULL,
    entity_id VARCHAR(50) NOT NULL,
    action VARCHAR(50) NOT NULL,
    old_state JSONB,
    new_state JSONB,
    user_id VARCHAR(20),
    ip_address VARCHAR(45),
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    details TEXT
);

-- Create index on entity_type and entity_id for entity audit trail
CREATE INDEX idx_entity ON audit_logs(entity_type, entity_id);

-- Create index on timestamp for time-based queries
CREATE INDEX idx_audit_timestamp ON audit_logs(timestamp);

-- Create index on user_id for user activity tracking
CREATE INDEX idx_audit_user ON audit_logs(user_id);

-- Create index on action for filtering by action type
CREATE INDEX idx_audit_action ON audit_logs(action);
