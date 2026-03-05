-- Create baggage table
CREATE TABLE baggage (
    baggage_id BIGSERIAL PRIMARY KEY,
    check_in_id VARCHAR(50) NOT NULL,
    weight_kg DECIMAL(5, 2) NOT NULL,
    dimensions VARCHAR(50),
    baggage_type VARCHAR(20) NOT NULL,
    excess_weight_kg DECIMAL(5, 2) DEFAULT 0,
    excess_fee DECIMAL(10, 2) DEFAULT 0,
    payment_status VARCHAR(20) DEFAULT 'pending',
    payment_transaction_id VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (check_in_id) REFERENCES check_ins(check_in_id) ON DELETE CASCADE,
    CONSTRAINT chk_baggage_type CHECK (baggage_type IN ('carry_on', 'checked', 'oversized')),
    CONSTRAINT chk_payment_status CHECK (payment_status IN ('pending', 'paid', 'failed', 'refunded'))
);

-- Create index on check_in_id for quick baggage lookup
CREATE INDEX idx_baggage_check_in ON baggage(check_in_id);

-- Create index on payment_status
CREATE INDEX idx_payment_status ON baggage(payment_status);
