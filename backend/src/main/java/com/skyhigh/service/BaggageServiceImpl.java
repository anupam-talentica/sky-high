package com.skyhigh.service;

import com.skyhigh.dto.BaggageDetailsDTO;
import com.skyhigh.dto.BaggageResponseDTO;
import com.skyhigh.entity.Baggage;
import com.skyhigh.enums.PaymentStatus;
import com.skyhigh.exception.BaggageNotFoundException;
import com.skyhigh.repository.BaggageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class BaggageServiceImpl implements BaggageService {

    private static final Logger logger = LoggerFactory.getLogger(BaggageServiceImpl.class);

    private final BaggageRepository baggageRepository;
    private final WeightService weightService;

    @Value("${app.baggage.max-weight:25}")
    private BigDecimal maxAllowedWeight;

    @Value("${app.baggage.excess-fee-per-kg:10}")
    private BigDecimal excessFeePerKg;

    public BaggageServiceImpl(BaggageRepository baggageRepository, WeightService weightService) {
        this.baggageRepository = baggageRepository;
        this.weightService = weightService;
    }

    @Override
    @Transactional
    public BaggageResponseDTO addBaggage(String checkInId, BaggageDetailsDTO baggageDetails) {
        logger.info("Adding baggage for check-in: {}", checkInId);

        BigDecimal currentWeight = baggageDetails.getWeightKg();
        
        // Get existing baggage to calculate cumulative weight
        List<Baggage> existingBaggage = baggageRepository.findByCheckInId(checkInId);
        BigDecimal previousTotalWeight = existingBaggage.stream()
                .map(Baggage::getWeightKg)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal newTotalWeight = previousTotalWeight.add(currentWeight);
        
        // Calculate excess based on cumulative weight
        BigDecimal cumulativeExcess = weightService.calculateExcessWeight(newTotalWeight, maxAllowedWeight);
        
        // Calculate how much of the excess is from this bag
        BigDecimal previousExcess = weightService.calculateExcessWeight(previousTotalWeight, maxAllowedWeight);
        BigDecimal thisItemExcess = cumulativeExcess.subtract(previousExcess);
        if (thisItemExcess.compareTo(BigDecimal.ZERO) < 0) {
            thisItemExcess = BigDecimal.ZERO;
        }
        
        BigDecimal excessFee = calculateExcessFee(thisItemExcess);

        Baggage baggage = new Baggage();
        baggage.setCheckInId(checkInId);
        baggage.setWeightKg(currentWeight);
        baggage.setDimensions(baggageDetails.getDimensions());
        baggage.setBaggageType(baggageDetails.getBaggageType());
        baggage.setExcessWeightKg(thisItemExcess);
        baggage.setExcessFee(excessFee);
        baggage.setPaymentStatus(excessFee.compareTo(BigDecimal.ZERO) > 0 ? PaymentStatus.PENDING : PaymentStatus.PAID);

        Baggage savedBaggage = baggageRepository.save(baggage);

        logger.info("Baggage added successfully: baggageId={}, weight={}, cumulativeWeight={}, excessFee={}", 
                   savedBaggage.getBaggageId(), currentWeight, newTotalWeight, excessFee);

        return BaggageResponseDTO.builder()
                .baggageId(savedBaggage.getBaggageId())
                .checkInId(savedBaggage.getCheckInId())
                .weightKg(savedBaggage.getWeightKg())
                .dimensions(savedBaggage.getDimensions())
                .baggageType(savedBaggage.getBaggageType())
                .excessWeightKg(savedBaggage.getExcessWeightKg())
                .excessFee(savedBaggage.getExcessFee())
                .paymentStatus(savedBaggage.getPaymentStatus())
                .message(String.format("Total weight: %.2f kg (Free allowance: 20 kg). %s", 
                        newTotalWeight,
                        excessFee.compareTo(BigDecimal.ZERO) > 0 
                            ? String.format("Excess fee: $%.2f", excessFee)
                            : "No excess fee"))
                .build();
    }

    @Override
    public BigDecimal calculateExcessFee(BigDecimal excessWeight) {
        if (excessWeight.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return excessWeight.multiply(excessFeePerKg).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BaggageResponseDTO> getAllBaggageForCheckIn(String checkInId) {
        logger.info("Fetching all baggage for check-in: {}", checkInId);
        
        List<Baggage> baggageList = baggageRepository.findByCheckInId(checkInId);
        
        return baggageList.stream()
                .map(this::convertToDTO)
                .collect(java.util.stream.Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Baggage getFirstBaggageByCheckInId(String checkInId) {
        return baggageRepository.findFirstByCheckInId(checkInId)
                .orElseThrow(() -> new BaggageNotFoundException("Baggage not found for check-in: " + checkInId));
    }

    @Override
    @Transactional
    public void deleteBaggage(Long baggageId) {
        logger.info("Deleting baggage: {}", baggageId);
        
        Baggage baggage = baggageRepository.findById(baggageId)
                .orElseThrow(() -> new BaggageNotFoundException("Baggage not found with ID: " + baggageId));
        
        baggageRepository.delete(baggage);
        
        logger.info("Baggage deleted successfully: baggageId={}", baggageId);
    }

    private BaggageResponseDTO convertToDTO(Baggage baggage) {
        return BaggageResponseDTO.builder()
                .baggageId(baggage.getBaggageId())
                .checkInId(baggage.getCheckInId())
                .weightKg(baggage.getWeightKg())
                .dimensions(baggage.getDimensions())
                .baggageType(baggage.getBaggageType())
                .excessWeightKg(baggage.getExcessWeightKg())
                .excessFee(baggage.getExcessFee())
                .paymentStatus(baggage.getPaymentStatus())
                .paymentTransactionId(baggage.getPaymentTransactionId())
                .build();
    }
}
