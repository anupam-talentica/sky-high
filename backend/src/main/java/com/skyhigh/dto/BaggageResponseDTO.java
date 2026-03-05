package com.skyhigh.dto;

import com.skyhigh.enums.BaggageType;
import com.skyhigh.enums.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BaggageResponseDTO {

    private Long baggageId;
    private String checkInId;
    private BigDecimal weightKg;
    private String dimensions;
    private BaggageType baggageType;
    private BigDecimal excessWeightKg;
    private BigDecimal excessFee;
    private PaymentStatus paymentStatus;
    private String paymentTransactionId;
    private String message;
}
