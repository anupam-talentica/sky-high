package com.skyhigh.service;

import com.skyhigh.dto.PaymentRequestDTO;
import com.skyhigh.dto.PaymentResponseDTO;
import com.skyhigh.enums.PaymentStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class PaymentServiceImpl implements PaymentService {

    private static final Logger logger = LoggerFactory.getLogger(PaymentServiceImpl.class);

    @Value("${app.payment.mock-success-rate:0.95}")
    private double mockSuccessRate;

    @Override
    public PaymentResponseDTO processPayment(String checkInId, PaymentRequestDTO paymentRequest) {
        logger.info("Processing payment for check-in: {}, amount: {}", 
                   checkInId, paymentRequest.getAmount());

        String transactionId = generateTransactionId();
        
        boolean isSuccess = Math.random() < mockSuccessRate;

        PaymentResponseDTO response = PaymentResponseDTO.builder()
                .transactionId(transactionId)
                .amount(paymentRequest.getAmount())
                .status(isSuccess ? PaymentStatus.PAID : PaymentStatus.FAILED)
                .processedAt(LocalDateTime.now())
                .message(isSuccess 
                        ? "Payment processed successfully" 
                        : "Payment failed. Please try again.")
                .build();

        logger.info("Payment processing completed: transactionId={}, status={}", 
                   transactionId, response.getStatus());

        return response;
    }

    @Override
    public boolean verifyPayment(String transactionId) {
        logger.debug("Verifying payment: transactionId={}", transactionId);
        return transactionId != null && transactionId.startsWith("TXN-");
    }

    private String generateTransactionId() {
        return "TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
