package com.skyhigh.event;

import com.skyhigh.service.WaitlistService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class WaitlistEventListener {
    
    private static final Logger logger = LoggerFactory.getLogger(WaitlistEventListener.class);
    
    private final WaitlistService waitlistService;
    
    public WaitlistEventListener(WaitlistService waitlistService) {
        this.waitlistService = waitlistService;
    }
    
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    public void handleSeatReleased(SeatReleasedEvent event) {
        logger.info("Handling seat released event for seat {} on flight {}", 
            event.getSeatNumber(), event.getFlightId());
        
        try {
            waitlistService.processWaitlist(event.getFlightId(), event.getSeatNumber());
        } catch (Exception e) {
            logger.error("Error processing waitlist for seat {} on flight {}: {}", 
                event.getSeatNumber(), event.getFlightId(), e.getMessage(), e);
        }
    }
}
