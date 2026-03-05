package com.skyhigh.controller;

import com.skyhigh.dto.*;
import com.skyhigh.service.CheckInService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/check-ins")
public class CheckInController {

    private static final Logger logger = LoggerFactory.getLogger(CheckInController.class);

    private final CheckInService checkInService;

    public CheckInController(CheckInService checkInService) {
        this.checkInService = checkInService;
    }

    @PostMapping("/initiate")
    public ResponseEntity<CheckInResponseDTO> startCheckIn(@Valid @RequestBody CheckInRequestDTO request) {
        logger.info("POST /api/v1/check-ins/initiate - Starting check-in for passenger: {}", request.getPassengerId());
        
        CheckInResponseDTO response = checkInService.startCheckIn(request);
        
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{checkInId}/select-seat")
    public ResponseEntity<CheckInResponseDTO> selectSeat(
            @PathVariable String checkInId,
            @Valid @RequestBody SeatReservationRequestDTO request) {
        
        logger.info("POST /api/v1/check-ins/{}/select-seat - Selecting seat", checkInId);
        
        CheckInResponseDTO response = checkInService.selectSeat(checkInId, request);
        
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{checkInId}/baggage")
    public ResponseEntity<BaggageResponseDTO> addBaggage(
            @PathVariable String checkInId,
            @Valid @RequestBody BaggageDetailsDTO baggageDetails) {
        
        logger.info("POST /api/v1/check-ins/{}/baggage - Adding baggage", checkInId);
        
        BaggageResponseDTO response = checkInService.addBaggage(checkInId, baggageDetails);
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{checkInId}/baggage")
    public ResponseEntity<List<BaggageResponseDTO>> getBaggage(@PathVariable String checkInId) {
        logger.info("GET /api/v1/check-ins/{}/baggage - Fetching baggage", checkInId);
        
        List<BaggageResponseDTO> baggageList = checkInService.getBaggageForCheckIn(checkInId);
        
        return ResponseEntity.ok(baggageList);
    }

    @DeleteMapping("/{checkInId}/baggage/{baggageId}")
    public ResponseEntity<Void> deleteBaggage(
            @PathVariable String checkInId,
            @PathVariable Long baggageId) {
        logger.info("DELETE /api/v1/check-ins/{}/baggage/{} - Deleting baggage", checkInId, baggageId);
        
        checkInService.deleteBaggage(checkInId, baggageId);
        
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{checkInId}/payment")
    public ResponseEntity<PaymentResponseDTO> processPayment(
            @PathVariable String checkInId,
            @Valid @RequestBody PaymentRequestDTO paymentRequest) {
        
        logger.info("POST /api/v1/check-ins/{}/payment - Processing payment", checkInId);
        
        PaymentResponseDTO response = checkInService.processPayment(checkInId, paymentRequest);
        
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{checkInId}/confirm")
    public ResponseEntity<CheckInResponseDTO> confirmCheckIn(@PathVariable String checkInId) {
        logger.info("POST /api/v1/check-ins/{}/confirm - Confirming check-in", checkInId);
        
        CheckInResponseDTO response = checkInService.confirmCheckIn(checkInId);
        
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{checkInId}/cancel")
    public ResponseEntity<CheckInResponseDTO> cancelCheckIn(@PathVariable String checkInId) {
        logger.info("POST /api/v1/check-ins/{}/cancel - Cancelling check-in", checkInId);
        
        CheckInResponseDTO response = checkInService.cancelCheckIn(checkInId);
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{checkInId}")
    public ResponseEntity<CheckInResponseDTO> getCheckInDetails(@PathVariable String checkInId) {
        logger.debug("GET /api/v1/check-ins/{} - Fetching check-in details", checkInId);
        
        CheckInResponseDTO response = checkInService.getCheckInDetails(checkInId);
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/passenger/{passengerId}")
    public ResponseEntity<List<PassengerCheckInSummaryDTO>> getCheckInsForPassenger(
            @PathVariable String passengerId) {
        logger.debug("GET /api/v1/check-ins/passenger/{} - Fetching check-ins for passenger", passengerId);

        List<PassengerCheckInSummaryDTO> checkIns = checkInService.getCheckInsForPassenger(passengerId);

        return ResponseEntity.ok(checkIns);
    }
}
