package com.skyhigh.service;

import com.skyhigh.dto.PaymentRequestDTO;
import com.skyhigh.dto.PaymentResponseDTO;

public interface PaymentService {
    
    PaymentResponseDTO processPayment(String checkInId, PaymentRequestDTO paymentRequest);
    
    boolean verifyPayment(String transactionId);
}
