package com.skyhigh.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Fired after a transaction commits when seat state changes affect one or more flights.
 * Listeners should invalidate the seat map cache for the given flight IDs.
 */
@Getter
public class SeatMapCacheInvalidationEvent extends ApplicationEvent {

    private final Set<String> flightIds;

    public SeatMapCacheInvalidationEvent(Object source, String flightId) {
        super(source);
        this.flightIds = Collections.singleton(flightId);
    }

    public SeatMapCacheInvalidationEvent(Object source, Collection<String> flightIds) {
        super(source);
        this.flightIds = flightIds == null ? Set.of() : Set.copyOf(flightIds);
    }

    public static SeatMapCacheInvalidationEvent forFlight(Object source, String flightId) {
        return new SeatMapCacheInvalidationEvent(source, flightId);
    }

    public static SeatMapCacheInvalidationEvent forFlights(Object source, Iterable<String> flightIds) {
        if (flightIds == null) {
            return new SeatMapCacheInvalidationEvent(source, Set.of());
        }
        Set<String> set = StreamSupport.stream(flightIds.spliterator(), false).collect(Collectors.toSet());
        return new SeatMapCacheInvalidationEvent(source, set);
    }
}
