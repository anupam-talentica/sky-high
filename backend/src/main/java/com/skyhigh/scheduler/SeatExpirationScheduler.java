package com.skyhigh.scheduler;

import com.skyhigh.entity.Seat;
import com.skyhigh.enums.CheckInStatus;
import com.skyhigh.enums.SeatState;
import com.skyhigh.event.SeatMapCacheInvalidationEvent;
import com.skyhigh.event.SeatReleasedEvent;
import com.skyhigh.repository.CheckInRepository;
import com.skyhigh.repository.SeatRepository;
import com.skyhigh.service.AuditLogService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class SeatExpirationScheduler {
    
    private static final Logger logger = LoggerFactory.getLogger(SeatExpirationScheduler.class);
    private static final int MAX_BATCH_SIZE = 100;
    
    private final SeatRepository seatRepository;
    private final AuditLogService auditLogService;
    private final ApplicationEventPublisher eventPublisher;
    private final CheckInRepository checkInRepository;
    
    private final Counter expiredSeatsCounter;
    private final Counter failedExpirationCounter;
    private final Timer expirationTimer;
    private final AtomicInteger lastRunReleasedCount = new AtomicInteger(0);
    private volatile LocalDateTime lastSuccessfulRun;
    private volatile boolean isHealthy = true;
    
    @Value("${app.seat-release-job-interval:5000}")
    private long jobInterval;
    
    @Value("${app.seat-expiration-batch-size:100}")
    private int batchSize;
    
    @Value("${app.seat-expiration-retry-attempts:3}")
    private int retryAttempts;
    
    public SeatExpirationScheduler(SeatRepository seatRepository,
                                  AuditLogService auditLogService,
                                  ApplicationEventPublisher eventPublisher,
                                  CheckInRepository checkInRepository,
                                  MeterRegistry meterRegistry) {
        this.seatRepository = seatRepository;
        this.auditLogService = auditLogService;
        this.eventPublisher = eventPublisher;
        this.checkInRepository = checkInRepository;
        
        this.expiredSeatsCounter = Counter.builder("scheduler.seats.expired")
            .description("Number of seats expired by scheduler")
            .register(meterRegistry);
        
        this.failedExpirationCounter = Counter.builder("scheduler.seats.expiration.failed")
            .description("Number of failed seat expirations")
            .register(meterRegistry);
        
        this.expirationTimer = Timer.builder("scheduler.seats.expiration.duration")
            .description("Time taken to process seat expirations")
            .register(meterRegistry);
    }
    
    @Scheduled(fixedDelayString = "${app.seat-release-job-interval:5000}")
    public void releaseExpiredSeats() {
        logger.debug("Starting scheduled task: Release expired seats");
        
        expirationTimer.record(() -> {
            try {
                int releasedCount = processExpiredSeatsWithRetry();
                lastRunReleasedCount.set(releasedCount);
                lastSuccessfulRun = LocalDateTime.now();
                isHealthy = true;
                
                if (releasedCount > 0) {
                    logger.info("Successfully released {} expired seats", releasedCount);
                }
                
            } catch (Exception e) {
                logger.error("Critical error in seat expiration scheduler: {}", e.getMessage(), e);
                isHealthy = false;
                failedExpirationCounter.increment();
            }
        });
    }
    
    private int processExpiredSeatsWithRetry() {
        int attempt = 0;
        Exception lastException = null;
        
        while (attempt < retryAttempts) {
            try {
                return processExpiredSeats();
            } catch (Exception e) {
                attempt++;
                lastException = e;
                logger.warn("Attempt {} failed to process expired seats: {}", attempt, e.getMessage());
                
                if (attempt < retryAttempts) {
                    try {
                        Thread.sleep(1000L * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Interrupted during retry backoff", ie);
                    }
                }
            }
        }
        
        throw new RuntimeException("Failed to process expired seats after " + retryAttempts + " attempts", lastException);
    }
    
    @Transactional
    int processExpiredSeats() {
        LocalDateTime now = LocalDateTime.now();
        
        List<Seat> expiredSeats = seatRepository.findExpiredHeldSeats(now);
        
        if (expiredSeats.isEmpty()) {
            logger.debug("No expired seats found");
            return 0;
        }
        
        int processedCount = 0;
        int limit = Math.min(expiredSeats.size(), batchSize);
        Set<String> affectedFlightIds = new LinkedHashSet<>();
        
        logger.info("Found {} expired seats, processing batch of {}", expiredSeats.size(), limit);
        
        for (int i = 0; i < limit; i++) {
            Seat seat = expiredSeats.get(i);
            try {
                if (processSingleExpiredSeat(seat)) {
                    processedCount++;
                    affectedFlightIds.add(seat.getFlightId());
                    expiredSeatsCounter.increment();
                }
            } catch (Exception e) {
                logger.error("Error processing expired seat {} (ID: {}): {}", 
                    seat.getSeatNumber(), seat.getSeatId(), e.getMessage());
                failedExpirationCounter.increment();
            }
        }
        
        if (!affectedFlightIds.isEmpty()) {
            eventPublisher.publishEvent(SeatMapCacheInvalidationEvent.forFlights(this, affectedFlightIds));
        }
        
        return processedCount;
    }
    
    private boolean processSingleExpiredSeat(Seat seat) {
        if (seat.getState() != SeatState.HELD) {
            logger.debug("Seat {} is no longer in HELD state, skipping", seat.getSeatNumber());
            return false;
        }
        
        if (seat.getHeldUntil() == null || !LocalDateTime.now().isAfter(seat.getHeldUntil())) {
            logger.debug("Seat {} has not expired yet, skipping", seat.getSeatNumber());
            return false;
        }
        
        String flightId = seat.getFlightId();
        String seatNumber = seat.getSeatNumber();
        SeatState oldState = seat.getState();
        String previousHolder = seat.getHeldBy();

        logger.info(">>>>>>>>>>> {}>>>>>{}>>>>>{}", seat.getSeatNumber(), flightId, previousHolder );
        
        // Cancel any check-in associated with this seat
        if (previousHolder != null) {
            checkInRepository.findFirstByPassengerIdAndFlightIdAndStatusNotOrderByCreatedAtDesc(
                previousHolder, flightId, CheckInStatus.CANCELLED)
                .ifPresent(checkIn -> {
                    if (checkIn.getSeatId() != null && checkIn.getSeatId().equals(seat.getSeatId())) {
                        if (checkIn.getStatus() != CheckInStatus.COMPLETED && 
                            checkIn.getStatus() != CheckInStatus.CANCELLED) {
                            
                            CheckInStatus oldStatus = checkIn.getStatus();
                            checkIn.setStatus(CheckInStatus.CANCELLED);
                            checkIn.setCancelledAt(LocalDateTime.now());
                            checkIn.setSeatId(null);
                            checkInRepository.save(checkIn);
                            
                            auditLogService.logStateChange(
                                "CheckIn",
                                checkIn.getCheckInId(),
                                oldStatus.toString(),
                                CheckInStatus.CANCELLED.toString(),
                                "SYSTEM_EXPIRATION"
                            );
                            
                            logger.info("Cancelled check-in {} due to seat expiration", checkIn.getCheckInId());
                        }
                    }
                });
        }
        
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
        
        logger.info("Released expired seat: {} for flight {} (previously held by {})", 
            seatNumber, flightId, previousHolder);

        // Publish event by seat + flight so waitlist listener can assign to next in line (e.g. B who joined waitlist when A had the seat)
        eventPublisher.publishEvent(new SeatReleasedEvent(this, flightId, seatNumber));
        logger.debug("Published SeatReleasedEvent for seat {} on flight {}", seatNumber, flightId);
        
        return true;
    }
    
    public boolean isHealthy() {
        if (!isHealthy) {
            return false;
        }
        
        if (lastSuccessfulRun == null) {
            return true;
        }
        
        long secondsSinceLastRun = java.time.Duration.between(lastSuccessfulRun, LocalDateTime.now()).getSeconds();
        long maxExpectedDelay = (jobInterval / 1000) * 3;
        
        return secondsSinceLastRun < maxExpectedDelay;
    }
    
    public int getLastRunReleasedCount() {
        return lastRunReleasedCount.get();
    }
    
    public LocalDateTime getLastSuccessfulRun() {
        return lastSuccessfulRun;
    }
}
