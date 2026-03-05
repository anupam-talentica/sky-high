package com.skyhigh.service;

import com.skyhigh.dto.SeatDTO;
import com.skyhigh.dto.SeatMapResponseDTO;
import com.skyhigh.dto.SeatReservationResponseDTO;
import com.skyhigh.entity.Seat;
import com.skyhigh.enums.SeatState;
import com.skyhigh.event.SeatMapCacheInvalidationEvent;
import com.skyhigh.event.SeatReleasedEvent;
import com.skyhigh.exception.InvalidStateTransitionException;
import com.skyhigh.exception.SeatConflictException;
import com.skyhigh.exception.SeatLockConflictException;
import com.skyhigh.exception.SeatNotFoundException;
import com.skyhigh.repository.SeatRepository;
import jakarta.persistence.OptimisticLockException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SeatServiceImpl implements SeatService {
    
    private static final Logger logger = LoggerFactory.getLogger(SeatServiceImpl.class);
    
    private final SeatRepository seatRepository;
    private final AuditLogService auditLogService;
    private final ApplicationEventPublisher eventPublisher;
    private final SeatMapCacheService seatMapCacheService;
    private final DistributedSeatLockService distributedSeatLockService;

    /** Retry-After seconds when distributed lock cannot be acquired (503 response). */
    private static final int LOCK_RETRY_AFTER_SECONDS = 3;

    @Value("${app.seat-hold-duration:120}")
    private int seatHoldDuration;

    public SeatServiceImpl(SeatRepository seatRepository,
                          AuditLogService auditLogService,
                          ApplicationEventPublisher eventPublisher,
                          SeatMapCacheService seatMapCacheService,
                          DistributedSeatLockService distributedSeatLockService) {
        this.seatRepository = seatRepository;
        this.auditLogService = auditLogService;
        this.eventPublisher = eventPublisher;
        this.seatMapCacheService = seatMapCacheService;
        this.distributedSeatLockService = distributedSeatLockService;
    }
    
    @Override
    @Transactional(readOnly = true)
    public SeatMapResponseDTO getAvailableSeats(String flightId) {
        logger.info("Fetching seat map for flight: {}", flightId);
        
        return seatMapCacheService.get(flightId)
            .orElseGet(() -> loadSeatMapFromDbAndCache(flightId));
    }
    
    private SeatMapResponseDTO loadSeatMapFromDbAndCache(String flightId) {
        List<Seat> seats = seatRepository.findByFlightId(flightId);
        
        if (seats.isEmpty()) {
            logger.warn("No seats found for flight: {}", flightId);
            throw new SeatNotFoundException("No seats found for flight: " + flightId);
        }
        
        long availableCount = seats.stream()
            .filter(s -> s.getState() == SeatState.AVAILABLE)
            .count();
        
        long heldCount = seats.stream()
            .filter(s -> s.getState() == SeatState.HELD)
            .count();
        
        long confirmedCount = seats.stream()
            .filter(s -> s.getState() == SeatState.CONFIRMED)
            .count();
        
        List<SeatDTO> seatDTOs = seats.stream()
            .map(this::convertToDTO)
            .collect(Collectors.toList());
        
        SeatMapResponseDTO dto = SeatMapResponseDTO.builder()
            .flightId(flightId)
            .totalSeats(seats.size())
            .availableSeats((int) availableCount)
            .heldSeats((int) heldCount)
            .confirmedSeats((int) confirmedCount)
            .seats(seatDTOs)
            .build();
        
        seatMapCacheService.put(flightId, dto);
        return dto;
    }
    
    @Override
    @Transactional(readOnly = true)
    public Seat getSeatById(Long seatId) {
        logger.debug("Fetching seat by ID: {}", seatId);
        return seatRepository.findById(seatId)
            .orElseThrow(() -> new SeatNotFoundException("Seat not found with ID: " + seatId));
    }
    
    @Override
    @Transactional(readOnly = true)
    public Seat getSeatByFlightAndNumber(String flightId, String seatNumber) {
        logger.debug("Fetching seat by flight: {} and seat number: {}", flightId, seatNumber);
        return seatRepository.findByFlightIdAndSeatNumber(flightId, seatNumber)
            .orElseThrow(() -> new SeatNotFoundException(
                String.format("Seat %s not found for flight %s", seatNumber, flightId)));
    }
    
    @Override
    @Transactional
    public SeatReservationResponseDTO reserveSeat(String flightId, String seatNumber, String passengerId) {
        logger.info("Attempting to reserve seat {} for flight {} by passenger {}",
            seatNumber, flightId, passengerId);

        Optional<String> lockToken = distributedSeatLockService.tryLock(flightId, seatNumber);
        if (lockToken.isEmpty()) {
            throw new SeatLockConflictException(LOCK_RETRY_AFTER_SECONDS);
        }
        String token = lockToken.get();

        try {
            Seat seat = seatRepository.findByFlightIdAndSeatNumberWithLock(flightId, seatNumber)
                .orElseThrow(() -> new SeatNotFoundException(flightId, seatNumber));

            if (seat.getState() != SeatState.AVAILABLE) {
                logger.warn("Seat {} is not available. Current state: {}", seatNumber, seat.getState());
                throw new SeatConflictException(
                    String.format("Seat %s is not available. Current state: %s", seatNumber, seat.getState())
                );
            }

            SeatState oldState = seat.getState();
            seat.transitionState(SeatState.HELD);
            seat.setHeldBy(passengerId);
            seat.setHeldUntil(LocalDateTime.now().plusSeconds(seatHoldDuration));

            Seat savedSeat = seatRepository.save(seat);

            auditLogService.logStateChange(
                "Seat",
                savedSeat.getSeatId().toString(),
                oldState.toString(),
                savedSeat.getState().toString(),
                passengerId
            );

            logger.info("Successfully reserved seat {} for passenger {}", seatNumber, passengerId);

            eventPublisher.publishEvent(SeatMapCacheInvalidationEvent.forFlight(this, flightId));

            return SeatReservationResponseDTO.builder()
                .seatId(savedSeat.getSeatId())
                .flightId(savedSeat.getFlightId())
                .seatNumber(savedSeat.getSeatNumber())
                .state(savedSeat.getState())
                .heldBy(savedSeat.getHeldBy())
                .heldUntil(savedSeat.getHeldUntil())
                .holdDurationSeconds(seatHoldDuration)
                .message("Seat reserved successfully. Please complete check-in within " + seatHoldDuration + " seconds.")
                .build();

        } catch (OptimisticLockException | ObjectOptimisticLockingFailureException e) {
            logger.error("Optimistic lock exception while reserving seat {}: {}", seatNumber, e.getMessage());
            throw new SeatConflictException("Seat was modified by another transaction. Please try again.", e);
        } catch (IllegalStateException e) {
            logger.error("Invalid state transition for seat {}: {}", seatNumber, e.getMessage());
            throw new InvalidStateTransitionException(e.getMessage(), e);
        } finally {
            distributedSeatLockService.unlock(flightId, seatNumber, token);
        }
    }
    
    @Override
    @Transactional
    public SeatReservationResponseDTO reserveSeatForCheckIn(String flightId, String seatNumber, String passengerId) {
        logger.info("Reserving seat {} for check-in on flight {} by passenger {}",
            seatNumber, flightId, passengerId);

        Optional<String> lockToken = distributedSeatLockService.tryLock(flightId, seatNumber);
        if (lockToken.isEmpty()) {
            throw new SeatLockConflictException(LOCK_RETRY_AFTER_SECONDS);
        }
        String token = lockToken.get();

        try {
            Seat seat = seatRepository.findByFlightIdAndSeatNumberWithLock(flightId, seatNumber)
                .orElseThrow(() -> new SeatNotFoundException(flightId, seatNumber));

            if (seat.getState() != SeatState.AVAILABLE) {
                logger.warn("Seat {} is not available. Current state: {}", seatNumber, seat.getState());
                throw new SeatConflictException(
                    String.format("Seat %s is not available. Current state: %s", seatNumber, seat.getState())
                );
            }

            SeatState oldState = seat.getState();
            seat.transitionState(SeatState.HELD);
            seat.setHeldBy(passengerId);
            // Set expiration time for check-in seats so scheduler can auto-cancel stale check-ins
            seat.setHeldUntil(LocalDateTime.now().plusSeconds(seatHoldDuration));

            Seat savedSeat = seatRepository.save(seat);

            auditLogService.logStateChange(
                "Seat",
                savedSeat.getSeatId().toString(),
                oldState.toString(),
                savedSeat.getState().toString(),
                passengerId
            );

            logger.info("Successfully reserved seat {} for check-in by passenger {}", seatNumber, passengerId);

            eventPublisher.publishEvent(SeatMapCacheInvalidationEvent.forFlight(this, flightId));

            return SeatReservationResponseDTO.builder()
                .seatId(savedSeat.getSeatId())
                .flightId(savedSeat.getFlightId())
                .seatNumber(savedSeat.getSeatNumber())
                .state(savedSeat.getState())
                .heldBy(savedSeat.getHeldBy())
                .heldUntil(savedSeat.getHeldUntil())
                .holdDurationSeconds(seatHoldDuration)
                .message("Seat reserved for check-in. Please complete your check-in within " + seatHoldDuration + " seconds.")
                .build();

        } catch (OptimisticLockException | ObjectOptimisticLockingFailureException e) {
            logger.error("Optimistic lock exception while reserving seat {}: {}", seatNumber, e.getMessage());
            throw new SeatConflictException("Seat was modified by another transaction. Please try again.", e);
        } catch (IllegalStateException e) {
            logger.error("Invalid state transition for seat {}: {}", seatNumber, e.getMessage());
            throw new InvalidStateTransitionException(e.getMessage(), e);
        } finally {
            distributedSeatLockService.unlock(flightId, seatNumber, token);
        }
    }
    
    @Override
    @Transactional
    public Seat releaseSeat(Long seatId) {
        logger.info("Releasing seat with ID: {}", seatId);
        
        Seat seat = seatRepository.findById(seatId)
            .orElseThrow(() -> new SeatNotFoundException("Seat not found with ID: " + seatId));
        
        if (seat.getState() == SeatState.AVAILABLE) {
            logger.info("Seat {} is already available. Skipping release.", seat.getSeatNumber());
            return seat;
        }
        
        if (seat.getState() != SeatState.HELD) {
            throw new InvalidStateTransitionException(
                String.format("Cannot release seat in state: %s", seat.getState())
            );
        }
        
        String flightId = seat.getFlightId();
        String seatNumber = seat.getSeatNumber();
        
        SeatState oldState = seat.getState();
        seat.transitionState(SeatState.AVAILABLE);
        seat.setHeldBy(null);
        seat.setHeldUntil(null);
        
        Seat savedSeat = seatRepository.save(seat);
        
        auditLogService.logStateChange(
            "Seat",
            savedSeat.getSeatId().toString(),
            oldState.toString(),
            savedSeat.getState().toString(),
            "SYSTEM"
        );
        
        logger.info("Successfully released seat: {}", seat.getSeatNumber());
        
        eventPublisher.publishEvent(SeatMapCacheInvalidationEvent.forFlight(this, flightId));
        eventPublisher.publishEvent(new SeatReleasedEvent(this, flightId, seatNumber));
        
        return savedSeat;
    }
    
    @Override
    @Transactional
    public Seat confirmSeat(Long seatId, String passengerId) {
        logger.info("Confirming seat {} for passenger {}", seatId, passengerId);
        
        Seat seat = seatRepository.findById(seatId)
            .orElseThrow(() -> new SeatNotFoundException("Seat not found with ID: " + seatId));
        
        if (seat.getState() != SeatState.HELD) {
            throw new InvalidStateTransitionException(
                String.format("Cannot confirm seat in state: %s", seat.getState())
            );
        }
        
        if (!seat.getHeldBy().equals(passengerId)) {
            throw new SeatConflictException(
                String.format("Seat is held by another passenger: %s", seat.getHeldBy())
            );
        }
        
        SeatState oldState = seat.getState();
        seat.transitionState(SeatState.CONFIRMED);
        seat.setConfirmedBy(passengerId);
        seat.setHeldUntil(null);
        
        Seat savedSeat = seatRepository.save(seat);
        
        auditLogService.logStateChange(
            "Seat",
            savedSeat.getSeatId().toString(),
            oldState.toString(),
            savedSeat.getState().toString(),
            passengerId
        );
        
        logger.info("Successfully confirmed seat: {}", seat.getSeatNumber());
        
        eventPublisher.publishEvent(SeatMapCacheInvalidationEvent.forFlight(this, seat.getFlightId()));
        
        return savedSeat;
    }
    
    @Override
    @Transactional
    public Seat cancelSeat(Long seatId) {
        logger.info("Cancelling seat with ID: {}", seatId);
        
        Seat seat = seatRepository.findById(seatId)
            .orElseThrow(() -> new SeatNotFoundException("Seat not found with ID: " + seatId));
        
        if (seat.getState() != SeatState.CONFIRMED) {
            throw new InvalidStateTransitionException(
                String.format("Cannot cancel seat in state: %s", seat.getState())
            );
        }
        
        String flightId = seat.getFlightId();
        String seatNumber = seat.getSeatNumber();
        
        SeatState oldState = seat.getState();
        seat.transitionState(SeatState.CANCELLED);
        
        Seat savedSeat = seatRepository.save(seat);
        
        auditLogService.logStateChange(
            "Seat",
            savedSeat.getSeatId().toString(),
            oldState.toString(),
            savedSeat.getState().toString(),
            seat.getConfirmedBy()
        );
        
        logger.info("Successfully cancelled seat: {}", seat.getSeatNumber());
        
        seat.transitionState(SeatState.AVAILABLE);
        seat.setConfirmedBy(null);
        Seat availableSeat = seatRepository.save(seat);
        
        auditLogService.logStateChange(
            "Seat",
            availableSeat.getSeatId().toString(),
            SeatState.CANCELLED.toString(),
            SeatState.AVAILABLE.toString(),
            "SYSTEM"
        );
        
        eventPublisher.publishEvent(SeatMapCacheInvalidationEvent.forFlight(this, flightId));
        eventPublisher.publishEvent(new SeatReleasedEvent(this, flightId, seatNumber));
        
        return savedSeat;
    }
    
    @Override
    @Transactional
    public int releaseExpiredSeats() {
        logger.debug("Running scheduled task to release expired seats");
        
        List<Seat> expiredSeats = seatRepository.findExpiredHeldSeats(LocalDateTime.now());
        
        if (expiredSeats.isEmpty()) {
            logger.debug("No expired seats found");
            return 0;
        }
        
        logger.info("Found {} expired seats to release", expiredSeats.size());
        
        Set<String> affectedFlightIds = new LinkedHashSet<>();
        for (Seat seat : expiredSeats) {
            try {
                String flightId = seat.getFlightId();
                String seatNumber = seat.getSeatNumber();
                affectedFlightIds.add(flightId);
                
                SeatState oldState = seat.getState();
                seat.transitionState(SeatState.AVAILABLE);
                seat.setHeldBy(null);
                seat.setHeldUntil(null);
                
                seatRepository.save(seat);
                
                auditLogService.logStateChange(
                    "Seat",
                    seat.getSeatId().toString(),
                    oldState.toString(),
                    seat.getState().toString(),
                    "SYSTEM_EXPIRATION"
                );
                
                logger.info("Released expired seat: {} for flight {}", 
                    seat.getSeatNumber(), seat.getFlightId());
                
                eventPublisher.publishEvent(new SeatReleasedEvent(this, flightId, seatNumber));
                
            } catch (Exception e) {
                logger.error("Error releasing expired seat {}: {}", 
                    seat.getSeatNumber(), e.getMessage());
            }
        }
        
        if (!affectedFlightIds.isEmpty()) {
            eventPublisher.publishEvent(SeatMapCacheInvalidationEvent.forFlights(this, affectedFlightIds));
        }
        
        return expiredSeats.size();
    }
    
    private SeatDTO convertToDTO(Seat seat) {
        return SeatDTO.builder()
            .seatId(seat.getSeatId())
            .seatNumber(seat.getSeatNumber())
            .seatType(seat.getSeatType())
            .state(seat.getState())
            .available(seat.getState() == SeatState.AVAILABLE)
            .heldBy(seat.getHeldBy())
            .confirmedBy(seat.getConfirmedBy())
            .build();
    }
}
