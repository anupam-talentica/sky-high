package com.skyhigh.dto;

import com.skyhigh.enums.BaggageType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BaggageDetailsDTO {

    @NotNull(message = "Weight is required")
    @DecimalMin(value = "0.1", message = "Weight must be greater than 0")
    private BigDecimal weightKg;

    private String dimensions;

    @NotNull(message = "Baggage type is required")
    private BaggageType baggageType;
}
