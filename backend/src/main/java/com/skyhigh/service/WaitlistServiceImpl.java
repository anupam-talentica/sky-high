package com.skyhigh.service;

import com.skyhigh.dto.WaitlistPositionDTO;
import com.skyhigh.dto.WaitlistResponseDTO;
import com.skyhigh.entity.Passenger;
import com.skyhigh.entity.Seat;
import com.skyhigh.entity.Waitlist;
import com.skyhigh.enums.SeatState;
import com.skyhigh.enums.WaitlistStatus;
import com.skyhigh.exception.SeatNotFoundException;
import com.skyhigh.exception.WaitlistAlreadyExistsException;
import com.skyhigh.exception.WaitlistNotFoundException;
import com.skyhigh.event.SeatMapCacheInvalidationEvent;
import com.skyhigh.repository.PassengerRepository;
import com.skyhigh.repository.SeatRepository;
import com.skyhigh.repository.WaitlistRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class WaitlistServiceImpl implements WaitlistService {
    
    private static final Logger logger = LoggerFactory.getLogger(WaitlistServiceImpl.class);
    
    private final WaitlistRepository waitlistRepository;
    private final SeatRepository seatRepository;
    private final PassengerRepository passengerRepository;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;
    private final ApplicationEventPublisher eventPublisher;
    
    @Value("${app.seat-hold-duration:120}")
    private int seatHoldDuration;
    
    public WaitlistServiceImpl(WaitlistRepository waitlistRepository,
                              SeatRepository seatRepository,
                              PassengerRepository passengerRepository,
                              NotificationService notificationService,
                              AuditLogService auditLogService,
                              ApplicationEventPublisher eventPublisher) {
        this.waitlistRepository = waitlistRepository;
        this.seatRepository = seatRepository;
        this.passengerRepository = passengerRepository;
        this.notificationService = notificationService;
        this.auditLogService = auditLogService;
        this.eventPublisher = eventPublisher;
    }
    
    @Override
    @Transactional
    public WaitlistResponseDTO joinWaitlist(String passengerId, String flightId, String seatNumber) {
        logger.info("Passenger {} joining waitlist for seat {} on flight {}", 
            passengerId, seatNumber, flightId);
        
        Seat seat = seatRepository.findByFlightIdAndSeatNumber(flightId, seatNumber)
            .orElseThrow(() -> new SeatNotFoundException(flightId, seatNumber));
        
        if (seat.getState() == SeatState.AVAILABLE) {
            throw new IllegalStateException(
                String.format("Seat %s is currently available. Please reserve it directly.", seatNumber));
        }
        
        boolean alreadyOnWaitlist = waitlistRepository.existsByPassengerIdAndFlightIdAndSeatNumber(
            passengerId, flightId, seatNumber);
        
        if (alreadyOnWaitlist) {
            throw new WaitlistAlreadyExistsException(passengerId, flightId, seatNumber);
        }
        
        Integer maxPosition = waitlistRepository.findMaxPositionBySeat(flightId, seatNumber)
            .orElse(0);
        
        Waitlist waitlist = new Waitlist();
        waitlist.setPassengerId(passengerId);
        waitlist.setFlightId(flightId);
        waitlist.setSeatNumber(seatNumber);
        waitlist.setPosition(maxPosition + 1);
        waitlist.setStatus(WaitlistStatus.WAITING);
        waitlist.setJoinedAt(LocalDateTime.now());
        
        Waitlist savedWaitlist = waitlistRepository.save(waitlist);
        
        auditLogService.logStateChange(
            "Waitlist",
            savedWaitlist.getWaitlistId().toString(),
            null,
            WaitlistStatus.WAITING.toString(),
            passengerId
        );
        
        logger.info("Passenger {} successfully joined waitlist at position {} for seat {} on flight {}", 
            passengerId, savedWaitlist.getPosition(), seatNumber, flightId);
        
        return convertToDTO(savedWaitlist, 
            "Successfully joined waitlist at position " + savedWaitlist.getPosition());
    }
    
    @Override
    @Transactional
    public void leaveWaitlist(Long waitlistId) {
        logger.info("Leaving waitlist entry: {}", waitlistId);
        
        Waitlist waitlist = waitlistRepository.findById(waitlistId)
            .orElseThrow(() -> new WaitlistNotFoundException(waitlistId));
        
        if (waitlist.getStatus() != WaitlistStatus.WAITING) {
            throw new IllegalStateException(
                String.format("Cannot leave waitlist in status: %s", waitlist.getStatus()));
        }
        
        WaitlistStatus oldStatus = waitlist.getStatus();
        waitlist.setStatus(WaitlistStatus.CANCELLED);
        waitlistRepository.save(waitlist);
        
        auditLogService.logStateChange(
            "Waitlist",
            waitlist.getWaitlistId().toString(),
            oldStatus.toString(),
            WaitlistStatus.CANCELLED.toString(),
            waitlist.getPassengerId()
        );
        
        logger.info("Passenger {} successfully left waitlist for seat {} on flight {}", 
            waitlist.getPassengerId(), waitlist.getSeatNumber(), waitlist.getFlightId());
    }
    
    @Override
    @Transactional
    public void processWaitlist(String flightId, String seatNumber) {
        logger.info("Processing waitlist for seat {} on flight {}", seatNumber, flightId);
        
        Optional<Waitlist> nextWaitlistEntry = waitlistRepository.findNextWaitingEntry(flightId, seatNumber);
        
        if (nextWaitlistEntry.isEmpty()) {
            logger.info("No waiting passengers found for seat {} on flight {}", seatNumber, flightId);
            return;
        }
        
        Waitlist waitlist = nextWaitlistEntry.get();
        String passengerId = waitlist.getPassengerId();
        
        logger.info("Assigning seat {} to passenger {} from waitlist", seatNumber, passengerId);
        
        Seat seat = seatRepository.findByFlightIdAndSeatNumberWithLock(flightId, seatNumber)
            .orElseThrow(() -> new SeatNotFoundException(flightId, seatNumber));
        
        if (seat.getState() != SeatState.AVAILABLE) {
            logger.warn("Seat {} is not available for waitlist assignment. Current state: {}", 
                seatNumber, seat.getState());
            return;
        }
        
        SeatState oldSeatState = seat.getState();
        seat.transitionState(SeatState.HELD);
        seat.setHeldBy(passengerId);
        seat.setHeldUntil(LocalDateTime.now().plusSeconds(seatHoldDuration));
        seatRepository.save(seat);
        
        auditLogService.logStateChange(
            "Seat",
            seat.getSeatId().toString(),
            oldSeatState.toString(),
            seat.getState().toString(),
            "WAITLIST_SYSTEM"
        );
        
        WaitlistStatus oldWaitlistStatus = waitlist.getStatus();
        waitlist.setStatus(WaitlistStatus.ASSIGNED);
        waitlist.setAssignedAt(LocalDateTime.now());
        waitlist.setNotifiedAt(LocalDateTime.now());
        waitlistRepository.save(waitlist);
        
        auditLogService.logStateChange(
            "Waitlist",
            waitlist.getWaitlistId().toString(),
            oldWaitlistStatus.toString(),
            WaitlistStatus.ASSIGNED.toString(),
            "WAITLIST_SYSTEM"
        );
        
        eventPublisher.publishEvent(SeatMapCacheInvalidationEvent.forFlight(this, flightId));
        
        Optional<Passenger> passenger = passengerRepository.findById(passengerId);
        if (passenger.isPresent()) {
            notificationService.sendSeatAssignmentNotification(
                passenger.get().getEmail(),
                passengerId,
                flightId,
                seatNumber
            );
        }
        
        logger.info("Successfully assigned seat {} to passenger {} from waitlist", 
            seatNumber, passengerId);
    }
    
    @Override
    @Transactional(readOnly = true)
    public WaitlistPositionDTO getWaitlistPosition(Long waitlistId) {
        logger.info("Getting waitlist position for entry: {}", waitlistId);
        
        Waitlist waitlist = waitlistRepository.findById(waitlistId)
            .orElseThrow(() -> new WaitlistNotFoundException(waitlistId));
        
        long totalWaiting = waitlistRepository.countWaitingEntriesBySeat(
            waitlist.getFlightId(), waitlist.getSeatNumber());
        
        return WaitlistPositionDTO.builder()
            .waitlistId(waitlist.getWaitlistId())
            .flightId(waitlist.getFlightId())
            .seatNumber(waitlist.getSeatNumber())
            .position(waitlist.getPosition())
            .totalWaiting(totalWaiting)
            .status(waitlist.getStatus())
            .message(String.format("You are at position %d with %d total passengers waiting", 
                waitlist.getPosition(), totalWaiting))
            .build();
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<WaitlistResponseDTO> getPassengerWaitlist(String passengerId) {
        logger.info("Getting waitlist entries for passenger: {}", passengerId);
        
        List<Waitlist> waitlistEntries = waitlistRepository.findByPassengerId(passengerId);
        
        return waitlistEntries.stream()
            .map(w -> convertToDTO(w, null))
            .collect(Collectors.toList());
    }
    
    @Override
    @Transactional
    public void expireWaitlistAssignment(Long waitlistId) {
        logger.info("Expiring waitlist assignment: {}", waitlistId);
        
        Waitlist waitlist = waitlistRepository.findById(waitlistId)
            .orElseThrow(() -> new WaitlistNotFoundException(waitlistId));
        
        if (waitlist.getStatus() != WaitlistStatus.ASSIGNED) {
            logger.warn("Cannot expire waitlist entry in status: {}", waitlist.getStatus());
            return;
        }
        
        WaitlistStatus oldStatus = waitlist.getStatus();
        waitlist.setStatus(WaitlistStatus.EXPIRED);
        waitlist.setExpiredAt(LocalDateTime.now());
        waitlistRepository.save(waitlist);
        
        auditLogService.logStateChange(
            "Waitlist",
            waitlist.getWaitlistId().toString(),
            oldStatus.toString(),
            WaitlistStatus.EXPIRED.toString(),
            "SYSTEM_EXPIRATION"
        );
        
        Optional<Passenger> passenger = passengerRepository.findById(waitlist.getPassengerId());
        if (passenger.isPresent()) {
            notificationService.sendWaitlistExpirationNotification(
                passenger.get().getEmail(),
                waitlist.getPassengerId(),
                waitlist.getFlightId(),
                waitlist.getSeatNumber()
            );
        }
        
        logger.info("Expired waitlist assignment for passenger {} on seat {} flight {}", 
            waitlist.getPassengerId(), waitlist.getSeatNumber(), waitlist.getFlightId());
    }
    
    private WaitlistResponseDTO convertToDTO(Waitlist waitlist, String message) {
        return WaitlistResponseDTO.builder()
            .waitlistId(waitlist.getWaitlistId())
            .passengerId(waitlist.getPassengerId())
            .flightId(waitlist.getFlightId())
            .seatNumber(waitlist.getSeatNumber())
            .position(waitlist.getPosition())
            .status(waitlist.getStatus())
            .joinedAt(waitlist.getJoinedAt())
            .notifiedAt(waitlist.getNotifiedAt())
            .assignedAt(waitlist.getAssignedAt())
            .expiredAt(waitlist.getExpiredAt())
            .message(message)
            .build();
    }
}
