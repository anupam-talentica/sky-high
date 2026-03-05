package com.skyhigh.event;

import com.skyhigh.service.SeatMapCacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Invalidates seat map cache after the transaction commits, so cache is only cleared
 * when seat state changes are persisted.
 */
@Component
public class SeatMapCacheInvalidationEventListener {

    private static final Logger logger = LoggerFactory.getLogger(SeatMapCacheInvalidationEventListener.class);

    private final SeatMapCacheService seatMapCacheService;

    public SeatMapCacheInvalidationEventListener(SeatMapCacheService seatMapCacheService) {
        this.seatMapCacheService = seatMapCacheService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSeatMapCacheInvalidation(SeatMapCacheInvalidationEvent event) {
        if (event.getFlightIds().isEmpty()) {
            return;
        }
        if (!seatMapCacheService.isCaching()) {
            return;
        }
        seatMapCacheService.invalidate(event.getFlightIds());
        logger.debug("Invalidated seat map cache for flights: {}", event.getFlightIds());
    }
}
