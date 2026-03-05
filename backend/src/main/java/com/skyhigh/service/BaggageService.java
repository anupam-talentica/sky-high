package com.skyhigh.service;

import com.skyhigh.dto.BaggageDetailsDTO;
import com.skyhigh.dto.BaggageResponseDTO;
import com.skyhigh.entity.Baggage;

import java.math.BigDecimal;
import java.util.List;

public interface BaggageService {
    
    BaggageResponseDTO addBaggage(String checkInId, BaggageDetailsDTO baggageDetails);
    
    List<BaggageResponseDTO> getAllBaggageForCheckIn(String checkInId);
    
    void deleteBaggage(Long baggageId);
    
    BigDecimal calculateExcessFee(BigDecimal excessWeight);
    
    Baggage getFirstBaggageByCheckInId(String checkInId);
}
