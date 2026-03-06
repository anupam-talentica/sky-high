package com.skyhigh.service;

import com.skyhigh.dto.*;
import com.skyhigh.entity.Baggage;
import com.skyhigh.entity.CheckIn;
import com.skyhigh.entity.Seat;
import com.skyhigh.enums.CheckInStatus;
import com.skyhigh.enums.PaymentStatus;
import com.skyhigh.exception.*;
import com.skyhigh.repository.CheckInRepository;
import com.skyhigh.repository.ReservationRepository;
import com.skyhigh.exception.UnauthorizedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class CheckInServiceImpl implements CheckInService {

    private static final Logger logger = LoggerFactory.getLogger(CheckInServiceImpl.class);

    private final CheckInRepository checkInRepository;
    private final SeatService seatService;
    private final BaggageService baggageService;
    private final PaymentService paymentService;
    private final AuditLogService auditLogService;
    private final ReservationRepository reservationRepository;

    public CheckInServiceImpl(
            CheckInRepository checkInRepository,
            SeatService seatService,
            BaggageService baggageService,
            PaymentService paymentService,
            AuditLogService auditLogService,
            ReservationRepository reservationRepository) {
        this.checkInRepository = checkInRepository;
        this.seatService = seatService;
        this.baggageService = baggageService;
        this.paymentService = paymentService;
        this.auditLogService = auditLogService;
        this.reservationRepository = reservationRepository;
    }

    @Override
    @Transactional
    public CheckInResponseDTO startCheckIn(CheckInRequestDTO request) {
        logger.info("Starting check-in for passenger: {}, flight: {}", 
                   request.getPassengerId(), request.getFlightId());

        // Ensure passenger has an active reservation for this flight
        boolean hasReservation = reservationRepository.existsByPassengerIdAndFlightIdAndStatus(
                request.getPassengerId(),
                request.getFlightId(),
                "ACTIVE"
        );
        if (!hasReservation) {
            throw new UnauthorizedException("Passenger does not have an active reservation for this flight");
        }

        // Check if there's an existing active check-in (not cancelled)
        var existingCheckIn = checkInRepository.findFirstByPassengerIdAndFlightIdAndStatusNotOrderByCreatedAtDesc(
                request.getPassengerId(), 
                request.getFlightId(),
                CheckInStatus.CANCELLED
        );

        if (existingCheckIn.isPresent()) {
            CheckIn checkIn = existingCheckIn.get();
            
            // If check-in is completed, don't allow creating a new one
            if (checkIn.getStatus() == CheckInStatus.COMPLETED) {
                throw new InvalidCheckInStateException(
                    "Check-in already completed for this passenger and flight");
            }
            
            // Return existing check-in to resume
            logger.info("Resuming existing check-in: checkInId={}", checkIn.getCheckInId());
            
            return CheckInResponseDTO.builder()
                    .checkInId(checkIn.getCheckInId())
                    .passengerId(checkIn.getPassengerId())
                    .flightId(checkIn.getFlightId())
                    .seatId(checkIn.getSeatId())
                    .seatNumber(checkIn.getSeatId() != null ? 
                        seatService.getSeatById(checkIn.getSeatId()).getSeatNumber() : null)
                    .status(checkIn.getStatus())
                    .checkInTime(checkIn.getCheckInTime())
                    .createdAt(checkIn.getCreatedAt())
                    .updatedAt(checkIn.getUpdatedAt())
                    .message("Resuming existing check-in. Continue where you left off.")
                    .build();
        }

        // Create new check-in
        String checkInId = generateCheckInId();
        
        CheckIn checkIn = new CheckIn();
        checkIn.setCheckInId(checkInId);
        checkIn.setPassengerId(request.getPassengerId());
        checkIn.setFlightId(request.getFlightId());
        checkIn.setSeatId(null);
        checkIn.setStatus(CheckInStatus.PENDING);
        checkIn.setCheckInTime(LocalDateTime.now());

        CheckIn savedCheckIn = checkInRepository.save(checkIn);

        auditLogService.logStateChange(
                "CheckIn",
                checkInId,
                null,
                String.format("{\"status\":\"%s\"}", CheckInStatus.PENDING),
                request.getPassengerId()
        );

        logger.info("Check-in initiated successfully: checkInId={}", checkInId);

        return CheckInResponseDTO.builder()
                .checkInId(savedCheckIn.getCheckInId())
                .passengerId(savedCheckIn.getPassengerId())
                .flightId(savedCheckIn.getFlightId())
                .seatId(null)
                .seatNumber(null)
                .status(savedCheckIn.getStatus())
                .checkInTime(savedCheckIn.getCheckInTime())
                .createdAt(savedCheckIn.getCreatedAt())
                .updatedAt(savedCheckIn.getUpdatedAt())
                .message("Check-in initiated successfully. Please select a seat.")
                .build();
    }

    @Override
    @Transactional
    public CheckInResponseDTO selectSeat(String checkInId, SeatReservationRequestDTO request) {
        logger.info("Selecting seat for check-in: {}, seatId: {}", checkInId, request.getSeatId());

        CheckIn checkIn = getCheckInById(checkInId);
        
        // Allow seat selection/change in PENDING or BAGGAGE_ADDED state
        if (checkIn.getStatus() != CheckInStatus.PENDING && 
            checkIn.getStatus() != CheckInStatus.BAGGAGE_ADDED) {
            throw new InvalidCheckInStateException(
                String.format("Cannot select seat. Check-in is in %s state", checkIn.getStatus()));
        }

        // Release old seat if exists
        if (checkIn.getSeatId() != null) {
            try {
                logger.info("Releasing old seat: {} for check-in: {}", checkIn.getSeatId(), checkInId);
                seatService.releaseSeat(checkIn.getSeatId());
            } catch (Exception e) {
                logger.warn("Failed to release old seat: {}", checkIn.getSeatId(), e);
            }
        }

        // Determine target seat either by ID or by seat number.
        // This avoids IllegalArgumentException from JPA when seatId is null
        // and returns a proper 4xx domain error instead of a generic 500.
        Seat seat;
        if (request.getSeatId() != null) {
            seat = seatService.getSeatById(request.getSeatId());
        } else if (request.getSeatNumber() != null && !request.getSeatNumber().isBlank()) {
            seat = seatService.getSeatByFlightAndNumber(checkIn.getFlightId(), request.getSeatNumber());
        } else {
            throw new SeatNotFoundException("Seat identifier (seatId or seatNumber) is required");
        }
        
        // Reserve new seat specifically for the check-in flow (no auto-expiration).
        SeatReservationResponseDTO seatReservation = seatService.reserveSeatForCheckIn(
                checkIn.getFlightId(),
                seat.getSeatNumber(),
                checkIn.getPassengerId()
        );

        Long oldSeatId = checkIn.getSeatId();
        checkIn.setSeatId(seatReservation.getSeatId());
        CheckIn savedCheckIn = checkInRepository.save(checkIn);

        auditLogService.logStateChange(
                "CheckIn",
                checkInId,
                oldSeatId != null ? String.format("{\"seatId\":%d}", oldSeatId) : "null",
                String.format("{\"seatId\":%d}", seatReservation.getSeatId()),
                checkIn.getPassengerId()
        );

        logger.info("Seat selected successfully: checkInId={}, seatId={}", 
                   checkInId, seatReservation.getSeatId());

        return CheckInResponseDTO.builder()
                .checkInId(savedCheckIn.getCheckInId())
                .passengerId(savedCheckIn.getPassengerId())
                .flightId(savedCheckIn.getFlightId())
                .seatId(savedCheckIn.getSeatId())
                .seatNumber(seat.getSeatNumber())
                .status(savedCheckIn.getStatus())
                .checkInTime(savedCheckIn.getCheckInTime())
                .createdAt(savedCheckIn.getCreatedAt())
                .updatedAt(savedCheckIn.getUpdatedAt())
                .message("Seat selected successfully.")
                .build();
    }

    @Override
    @Transactional
    public BaggageResponseDTO addBaggage(String checkInId, BaggageDetailsDTO baggageDetails) {
        logger.info("Adding baggage for check-in: {}", checkInId);

        CheckIn checkIn = getCheckInById(checkInId);

        // Allow adding baggage in PENDING or BAGGAGE_ADDED state
        if (checkIn.getStatus() != CheckInStatus.PENDING && 
            checkIn.getStatus() != CheckInStatus.BAGGAGE_ADDED) {
            throw new InvalidCheckInStateException(
                String.format("Cannot add baggage. Check-in is in %s state", checkIn.getStatus())
            );
        }

        BaggageResponseDTO baggageResponse = baggageService.addBaggage(checkInId, baggageDetails);

        // Only transition to BAGGAGE_ADDED if currently PENDING
        if (checkIn.getStatus() == CheckInStatus.PENDING) {
            CheckInStatus oldStatus = checkIn.getStatus();
            transitionCheckInStatus(checkIn, CheckInStatus.BAGGAGE_ADDED);
            checkInRepository.save(checkIn);

            auditLogService.logStateChange(
                    "CheckIn",
                    checkInId,
                    String.format("{\"status\":\"%s\"}", oldStatus),
                    String.format("{\"status\":\"%s\",\"baggageId\":%d}", CheckInStatus.BAGGAGE_ADDED, baggageResponse.getBaggageId()),
                    checkIn.getPassengerId()
            );
        }

        logger.info("Baggage added successfully for check-in: {}", checkInId);

        return baggageResponse;
    }

    @Override
    @Transactional(readOnly = true)
    public List<BaggageResponseDTO> getBaggageForCheckIn(String checkInId) {
        logger.info("Fetching baggage for check-in: {}", checkInId);
        
        return baggageService.getAllBaggageForCheckIn(checkInId);
    }

    @Override
    @Transactional
    public void deleteBaggage(String checkInId, Long baggageId) {
        logger.info("Deleting baggage {} for check-in: {}", baggageId, checkInId);
        
        CheckIn checkIn = getCheckInById(checkInId);
        
        baggageService.deleteBaggage(baggageId);
        
        List<BaggageResponseDTO> remainingBaggage = baggageService.getAllBaggageForCheckIn(checkInId);
        
        if (remainingBaggage.isEmpty() && checkIn.getStatus() == CheckInStatus.BAGGAGE_ADDED) {
            CheckInStatus oldStatus = checkIn.getStatus();
            transitionCheckInStatus(checkIn, CheckInStatus.PENDING);
            checkInRepository.save(checkIn);
            
            auditLogService.logStateChange(
                    "CheckIn",
                    checkInId,
                    String.format("{\"status\":\"%s\"}", oldStatus),
                    String.format("{\"status\":\"%s\",\"reason\":\"All baggage deleted\"}", CheckInStatus.PENDING),
                    checkIn.getPassengerId()
            );
            
            logger.info("All baggage deleted, check-in status reverted to PENDING: {}", checkInId);
        }
        
        logger.info("Baggage deleted successfully for check-in: {}", checkInId);
    }

    @Override
    @Transactional
    public PaymentResponseDTO processPayment(String checkInId, PaymentRequestDTO paymentRequest) {
        logger.info("Processing payment for check-in: {}", checkInId);

        CheckIn checkIn = getCheckInById(checkInId);

        if (checkIn.getStatus() != CheckInStatus.BAGGAGE_ADDED) {
            throw new InvalidCheckInStateException(
                String.format("Cannot process payment. Check-in is in %s state. Please add baggage first.", 
                             checkIn.getStatus())
            );
        }

        Baggage baggage = baggageService.getFirstBaggageByCheckInId(checkInId);

        if (baggage.getExcessFee().compareTo(BigDecimal.ZERO) == 0) {
            baggage.setPaymentStatus(PaymentStatus.PAID);
            baggage.setPaymentTransactionId("NO-PAYMENT-REQUIRED");
            
            transitionCheckInStatus(checkIn, CheckInStatus.PAYMENT_COMPLETED);
            checkInRepository.save(checkIn);

            logger.info("No payment required for check-in: {}", checkInId);

            return PaymentResponseDTO.builder()
                    .transactionId("NO-PAYMENT-REQUIRED")
                    .amount(BigDecimal.ZERO)
                    .status(PaymentStatus.PAID)
                    .message("No payment required")
                    .processedAt(LocalDateTime.now())
                    .build();
        }

        if (!paymentRequest.getAmount().equals(baggage.getExcessFee())) {
            throw new PaymentFailedException(
                String.format("Payment amount mismatch. Expected: %.2f, Provided: %.2f", 
                             baggage.getExcessFee(), paymentRequest.getAmount())
            );
        }

        PaymentResponseDTO paymentResponse = paymentService.processPayment(checkInId, paymentRequest);

        if (paymentResponse.getStatus() == PaymentStatus.PAID) {
            baggage.setPaymentStatus(PaymentStatus.PAID);
            baggage.setPaymentTransactionId(paymentResponse.getTransactionId());

            transitionCheckInStatus(checkIn, CheckInStatus.PAYMENT_COMPLETED);
            checkInRepository.save(checkIn);

            auditLogService.logStateChange(
                    "CheckIn",
                    checkInId,
                    String.format("{\"status\":\"%s\"}", CheckInStatus.BAGGAGE_ADDED),
                    String.format("{\"status\":\"%s\",\"transactionId\":\"%s\"}", 
                                 CheckInStatus.PAYMENT_COMPLETED, paymentResponse.getTransactionId()),
                    checkIn.getPassengerId()
            );

            logger.info("Payment completed successfully for check-in: {}", checkInId);
        } else {
            throw new PaymentFailedException("Payment processing failed: " + paymentResponse.getMessage());
        }

        return paymentResponse;
    }

    @Override
    @Transactional
    public CheckInResponseDTO confirmCheckIn(String checkInId) {
        logger.info("Confirming check-in: {}", checkInId);

        CheckIn checkIn = getCheckInById(checkInId);

        // Handle different states
        if (checkIn.getStatus() == CheckInStatus.PENDING) {
            // Check if there's any baggage added
            List<BaggageResponseDTO> baggageList = baggageService.getAllBaggageForCheckIn(checkInId);
            
            if (baggageList.isEmpty()) {
                // No baggage added, skip directly to PAYMENT_COMPLETED
                logger.info("No baggage added for check-in: {}. Transitioning to PAYMENT_COMPLETED.", checkInId);
                transitionCheckInStatus(checkIn, CheckInStatus.PAYMENT_COMPLETED);
                checkInRepository.save(checkIn);
                
                auditLogService.logStateChange(
                        "CheckIn",
                        checkInId,
                        String.format("{\"status\":\"%s\"}", CheckInStatus.PENDING),
                        String.format("{\"status\":\"%s\",\"reason\":\"No baggage added\"}", CheckInStatus.PAYMENT_COMPLETED),
                        checkIn.getPassengerId()
                );
            } else {
                // Baggage exists but status is still PENDING - this shouldn't happen normally
                throw new InvalidCheckInStateException(
                    "Cannot confirm check-in. Check-in is in PENDING state with baggage. Please complete the check-in flow."
                );
            }
        } else if (checkIn.getStatus() == CheckInStatus.BAGGAGE_ADDED) {
            // Check if there's any unpaid baggage
            List<BaggageResponseDTO> baggageList = baggageService.getAllBaggageForCheckIn(checkInId);
            BigDecimal totalUnpaidFees = baggageList.stream()
                .filter(b -> b.getPaymentStatus() == com.skyhigh.enums.PaymentStatus.PENDING)
                .map(BaggageResponseDTO::getExcessFee)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            if (totalUnpaidFees.compareTo(BigDecimal.ZERO) > 0) {
                throw new InvalidCheckInStateException(
                    String.format("Cannot confirm check-in. Outstanding baggage fee: $%.2f. Please complete payment first.", 
                                 totalUnpaidFees)
                );
            }
            // If no unpaid fees, transition to PAYMENT_COMPLETED first (state machine requirement)
            logger.info("No payment required for check-in: {}. Transitioning to PAYMENT_COMPLETED.", checkInId);
            transitionCheckInStatus(checkIn, CheckInStatus.PAYMENT_COMPLETED);
            checkInRepository.save(checkIn);
            
            auditLogService.logStateChange(
                    "CheckIn",
                    checkInId,
                    String.format("{\"status\":\"%s\"}", CheckInStatus.BAGGAGE_ADDED),
                    String.format("{\"status\":\"%s\",\"reason\":\"No payment required\"}", CheckInStatus.PAYMENT_COMPLETED),
                    checkIn.getPassengerId()
            );
        } else if (checkIn.getStatus() != CheckInStatus.PAYMENT_COMPLETED) {
            throw new InvalidCheckInStateException(
                String.format("Cannot confirm check-in. Check-in is in %s state. Please complete the check-in flow.", 
                             checkIn.getStatus())
            );
        }

        Seat confirmedSeat = seatService.confirmSeat(checkIn.getSeatId(), checkIn.getPassengerId());

        CheckInStatus oldStatus = checkIn.getStatus();
        transitionCheckInStatus(checkIn, CheckInStatus.COMPLETED);
        checkIn.setCompletedAt(LocalDateTime.now());
        
        CheckIn savedCheckIn = checkInRepository.save(checkIn);

        auditLogService.logStateChange(
                "CheckIn",
                checkInId,
                String.format("{\"status\":\"%s\"}", oldStatus),
                String.format("{\"status\":\"%s\",\"completedAt\":\"%s\"}", 
                             CheckInStatus.COMPLETED, savedCheckIn.getCompletedAt()),
                checkIn.getPassengerId()
        );

        logger.info("Check-in confirmed successfully: checkInId={}, seatId={}", 
                   checkInId, confirmedSeat.getSeatId());

        return CheckInResponseDTO.builder()
                .checkInId(savedCheckIn.getCheckInId())
                .passengerId(savedCheckIn.getPassengerId())
                .flightId(savedCheckIn.getFlightId())
                .seatId(savedCheckIn.getSeatId())
                .seatNumber(confirmedSeat.getSeatNumber())
                .status(savedCheckIn.getStatus())
                .checkInTime(savedCheckIn.getCheckInTime())
                .completedAt(savedCheckIn.getCompletedAt())
                .createdAt(savedCheckIn.getCreatedAt())
                .updatedAt(savedCheckIn.getUpdatedAt())
                .message("Check-in completed successfully!")
                .build();
    }

    @Override
    @Transactional
    public CheckInResponseDTO cancelCheckIn(String checkInId) {
        logger.info("Cancelling check-in: {}", checkInId);

        CheckIn checkIn = getCheckInById(checkInId);

        if (checkIn.getStatus() == CheckInStatus.CANCELLED) {
            throw new InvalidCheckInStateException("Check-in is already cancelled");
        }

        if (checkIn.getStatus() == CheckInStatus.COMPLETED) {
            throw new InvalidCheckInStateException("Cannot cancel a completed check-in");
        }

        CheckInStatus oldStatus = checkIn.getStatus();

        seatService.releaseSeat(checkIn.getSeatId());

        transitionCheckInStatus(checkIn, CheckInStatus.CANCELLED);
        checkIn.setCancelledAt(LocalDateTime.now());
        
        CheckIn savedCheckIn = checkInRepository.save(checkIn);

        auditLogService.logStateChange(
                "CheckIn",
                checkInId,
                String.format("{\"status\":\"%s\"}", oldStatus),
                String.format("{\"status\":\"%s\",\"cancelledAt\":\"%s\"}", 
                             CheckInStatus.CANCELLED, savedCheckIn.getCancelledAt()),
                checkIn.getPassengerId()
        );

        logger.info("Check-in cancelled successfully: checkInId={}", checkInId);

        return CheckInResponseDTO.builder()
                .checkInId(savedCheckIn.getCheckInId())
                .passengerId(savedCheckIn.getPassengerId())
                .flightId(savedCheckIn.getFlightId())
                .seatId(savedCheckIn.getSeatId())
                .status(savedCheckIn.getStatus())
                .checkInTime(savedCheckIn.getCheckInTime())
                .cancelledAt(savedCheckIn.getCancelledAt())
                .createdAt(savedCheckIn.getCreatedAt())
                .updatedAt(savedCheckIn.getUpdatedAt())
                .message("Check-in cancelled successfully. Seat has been released.")
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public CheckInResponseDTO getCheckInDetails(String checkInId) {
        logger.debug("Fetching check-in details: {}", checkInId);

        CheckIn checkIn = getCheckInById(checkInId);
        
        // Get seat number if seat is selected
        String seatNumber = null;
        if (checkIn.getSeatId() != null) {
            try {
                Seat seat = seatService.getSeatById(checkIn.getSeatId());
                seatNumber = seat.getSeatNumber();
            } catch (Exception e) {
                logger.warn("Failed to fetch seat details for seatId: {}", checkIn.getSeatId(), e);
            }
        }

        // Get baggage details
        List<BaggageResponseDTO> baggageDetails = baggageService.getAllBaggageForCheckIn(checkInId);
        BigDecimal totalBaggageFee = baggageDetails.stream()
                .map(BaggageResponseDTO::getExcessFee)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Generate boarding pass if check-in is completed
        String boardingPass = null;
        if (checkIn.getStatus() == CheckInStatus.COMPLETED) {
            boardingPass = generateBoardingPass(checkIn, seatNumber);
        }

        return CheckInResponseDTO.builder()
                .checkInId(checkIn.getCheckInId())
                .passengerId(checkIn.getPassengerId())
                .flightId(checkIn.getFlightId())
                .seatId(checkIn.getSeatId())
                .seatNumber(seatNumber)
                .status(checkIn.getStatus())
                .checkInTime(checkIn.getCheckInTime())
                .completedAt(checkIn.getCompletedAt())
                .cancelledAt(checkIn.getCancelledAt())
                .createdAt(checkIn.getCreatedAt())
                .updatedAt(checkIn.getUpdatedAt())
                .baggageDetails(baggageDetails)
                .totalBaggageFee(totalBaggageFee)
                .boardingPass(boardingPass)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public CheckIn getCheckInById(String checkInId) {
        return checkInRepository.findById(checkInId)
                .orElseThrow(() -> new CheckInNotFoundException("Check-in not found: " + checkInId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PassengerCheckInSummaryDTO> getCheckInsForPassenger(String passengerId) {
        String currentPassengerId = getCurrentPassengerId();

        if (!currentPassengerId.equals(passengerId)) {
            throw new UnauthorizedException("You are not allowed to view check-ins for another passenger");
        }

        logger.info("Fetching reservations and check-ins for passenger: {}", passengerId);

        // Drive the response from reservations, so we only return flights
        // for which the passenger has an active reservation. Attach the
        // most recent check-in info per flight when it exists.
        var reservations = reservationRepository.findByPassengerIdAndStatus(passengerId, "ACTIVE");

        return reservations.stream()
                .map(reservation -> {
                    var latestCheckInOpt = checkInRepository
                            .findFirstByPassengerIdAndFlightIdOrderByCreatedAtDesc(
                                    passengerId,
                                    reservation.getFlightId()
                            );

                    // If there is no check-in or the latest one is CANCELLED, treat as "no active check-in"
                    if (latestCheckInOpt.isEmpty() ||
                            latestCheckInOpt.get().getStatus() == CheckInStatus.CANCELLED) {
                        return PassengerCheckInSummaryDTO.builder()
                                .checkInId(null)
                                .passengerId(reservation.getPassengerId())
                                .flightId(reservation.getFlightId())
                                .seatId(null)
                                .status(null)
                                .initiatedAt(null)
                                .completedAt(null)
                                .createdAt(reservation.getCreatedAt())
                                .updatedAt(reservation.getUpdatedAt())
                                .build();
                    }

                    CheckIn checkIn = latestCheckInOpt.get();

                    return PassengerCheckInSummaryDTO.builder()
                            .checkInId(checkIn.getCheckInId())
                            .passengerId(checkIn.getPassengerId())
                            .flightId(checkIn.getFlightId())
                            .seatId(checkIn.getSeatId())
                            .status(checkIn.getStatus())
                            .initiatedAt(checkIn.getCheckInTime())
                            .completedAt(checkIn.getCompletedAt())
                            .createdAt(checkIn.getCreatedAt())
                            .updatedAt(checkIn.getUpdatedAt())
                            .build();
                })
                .collect(Collectors.toList());
    }

    private String getCurrentPassengerId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new UnauthorizedException("No authenticated passenger found");
        }
        return (String) authentication.getPrincipal();
    }

    private void transitionCheckInStatus(CheckIn checkIn, CheckInStatus newStatus) {
        if (!checkIn.getStatus().canTransitionTo(newStatus)) {
            throw new InvalidCheckInStateException(
                String.format("Cannot transition from %s to %s", checkIn.getStatus(), newStatus)
            );
        }
        checkIn.setStatus(newStatus);
    }

    private String generateCheckInId() {
        return "CHK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private String generateBoardingPass(CheckIn checkIn, String seatNumber) {
        return String.format("""
                ═══════════════════════════════════════
                         BOARDING PASS
                ═══════════════════════════════════════
                
                Passenger ID: %s
                Flight: %s
                Seat: %s
                Check-In ID: %s
                
                Boarding Time: %s
                Status: CONFIRMED
                
                ═══════════════════════════════════════
                Please arrive at gate 30 minutes before
                departure. Have this pass ready for
                scanning at security and boarding.
                ═══════════════════════════════════════
                """,
                checkIn.getPassengerId(),
                checkIn.getFlightId(),
                seatNumber != null ? seatNumber : "N/A",
                checkIn.getCheckInId(),
                checkIn.getCompletedAt() != null ? checkIn.getCompletedAt().toString() : "N/A"
        );
    }
}
