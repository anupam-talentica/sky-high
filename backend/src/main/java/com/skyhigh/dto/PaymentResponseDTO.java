package com.skyhigh.dto;

import com.skyhigh.enums.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentResponseDTO {

    private String transactionId;
    private BigDecimal amount;
    private PaymentStatus status;
    private String message;
    private LocalDateTime processedAt;
}
