package com.skyhigh.service;

import com.skyhigh.dto.PaymentRequestDTO;
import com.skyhigh.dto.PaymentResponseDTO;
import com.skyhigh.enums.PaymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class PaymentServiceImplTest {

    private PaymentServiceImpl paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentServiceImpl();
    }

    @Test
    void processPayment_WhenRandomBelowSuccessRate_ShouldReturnPaidResponse() {
        ReflectionTestUtils.setField(paymentService, "mockSuccessRate", 1.0d);

        PaymentRequestDTO request = new PaymentRequestDTO(
                new BigDecimal("199.99"),
                "CARD",
                "4111111111111111",
                "John Doe"
        );

        PaymentResponseDTO response = paymentService.processPayment("CHK-123", request);

        assertNotNull(response);
        assertEquals(new BigDecimal("199.99"), response.getAmount());
        assertEquals(PaymentStatus.PAID, response.getStatus());
        assertEquals("Payment processed successfully", response.getMessage());
        assertNotNull(response.getProcessedAt());
        assertNotNull(response.getTransactionId());
        assertTrue(response.getTransactionId().startsWith("TXN-"));

        assertTrue(paymentService.verifyPayment(response.getTransactionId()));
    }

    @Test
    void processPayment_WhenRandomAboveSuccessRate_ShouldReturnFailedResponse() {
        ReflectionTestUtils.setField(paymentService, "mockSuccessRate", 0.0d);

        PaymentRequestDTO request = new PaymentRequestDTO(
                new BigDecimal("50.00"),
                "CARD",
                "5555555555554444",
                "Jane Smith"
        );

        PaymentResponseDTO response = paymentService.processPayment("CHK-999", request);

        assertNotNull(response);
        assertEquals(new BigDecimal("50.00"), response.getAmount());
        assertEquals(PaymentStatus.FAILED, response.getStatus());
        assertEquals("Payment failed. Please try again.", response.getMessage());
        assertNotNull(response.getProcessedAt());
        assertNotNull(response.getTransactionId());
        assertTrue(response.getTransactionId().startsWith("TXN-"));
    }

    @Test
    void verifyPayment_ShouldReturnFalseForInvalidTransactionIds() {
        assertFalse(paymentService.verifyPayment(null));
        assertFalse(paymentService.verifyPayment("INVALID-123"));
    }
}

