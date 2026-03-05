package com.skyhigh.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class WeightServiceTest {

    private final WeightService weightService = new WeightService();

    @Test
    void calculateExcessWeight_WhenAboveLimit_ShouldReturnExcess() {
        BigDecimal actualWeight = new BigDecimal("25.50");
        BigDecimal maxAllowed = new BigDecimal("20.00");

        BigDecimal result = weightService.calculateExcessWeight(actualWeight, maxAllowed);

        assertEquals(new BigDecimal("5.50"), result);
    }

    @Test
    void calculateExcessWeight_WhenAtOrBelowLimit_ShouldReturnZero() {
        BigDecimal atLimit = weightService.calculateExcessWeight(
            new BigDecimal("20.00"),
            new BigDecimal("20.00")
        );
        BigDecimal belowLimit = weightService.calculateExcessWeight(
            new BigDecimal("18.75"),
            new BigDecimal("20.00")
        );

        assertEquals(BigDecimal.ZERO, atLimit);
        assertEquals(BigDecimal.ZERO, belowLimit);
    }

    @Test
    void isWithinLimit_ShouldReturnTrueWhenEqualOrBelowAndFalseWhenAbove() {
        assertTrue(weightService.isWithinLimit(new BigDecimal("20.00"), new BigDecimal("20.00")));
        assertTrue(weightService.isWithinLimit(new BigDecimal("19.99"), new BigDecimal("20.00")));
        assertFalse(weightService.isWithinLimit(new BigDecimal("20.01"), new BigDecimal("20.00")));
    }
}
