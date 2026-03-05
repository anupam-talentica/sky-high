package com.skyhigh.entity;

import com.skyhigh.enums.BaggageType;
import com.skyhigh.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "baggage",
       indexes = {
           @Index(name = "idx_baggage_check_in", columnList = "check_in_id"),
           @Index(name = "idx_payment_status", columnList = "payment_status")
       })
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Baggage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "baggage_id")
    private Long baggageId;

    @Column(name = "check_in_id", nullable = false, length = 50)
    private String checkInId;

    @Column(name = "weight_kg", nullable = false, precision = 5, scale = 2)
    private BigDecimal weightKg;

    @Column(name = "dimensions", length = 50)
    private String dimensions;

    @Convert(converter = BaggageTypeConverter.class)
    @Column(name = "baggage_type", nullable = false, length = 20)
    private BaggageType baggageType;

    @Column(name = "excess_weight_kg", precision = 5, scale = 2)
    private BigDecimal excessWeightKg = BigDecimal.ZERO;

    @Column(name = "excess_fee", precision = 10, scale = 2)
    private BigDecimal excessFee = BigDecimal.ZERO;

    @Convert(converter = PaymentStatusConverter.class)
    @Column(name = "payment_status", nullable = false, length = 20)
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;

    @Column(name = "payment_transaction_id", length = 100)
    private String paymentTransactionId;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
