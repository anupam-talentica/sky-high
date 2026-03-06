package com.skyhigh.event;

import com.skyhigh.service.WaitlistService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class WaitlistEventListener {

    private static final Logger logger = LoggerFactory.getLogger(WaitlistEventListener.class);

    private final WaitlistService waitlistService;

    public WaitlistEventListener(WaitlistService waitlistService) {
        this.waitlistService = waitlistService;
    }

    /**
     * Listens for seat release by flight + seat (e.g. when A's hold expires, B on waitlist gets the opportunity).
     * Uses @EventListener (not @TransactionalEventListener) so the event is received when the scheduler publishes it:
     * the scheduler's processExpiredSeats() is self-invoked so no transaction is active there, and AFTER_COMMIT would never fire.
     */
    @EventListener
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
