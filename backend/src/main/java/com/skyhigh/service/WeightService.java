package com.skyhigh.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class WeightService {

    public BigDecimal calculateExcessWeight(BigDecimal actualWeight, BigDecimal maxAllowedWeight) {
        BigDecimal excess = actualWeight.subtract(maxAllowedWeight);
        return excess.compareTo(BigDecimal.ZERO) > 0 ? excess : BigDecimal.ZERO;
    }

    public boolean isWithinLimit(BigDecimal weight, BigDecimal maxWeight) {
        return weight.compareTo(maxWeight) <= 0;
    }
}
