package com.skyhigh.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class SeatReleasedEvent extends ApplicationEvent {
    
    private final String flightId;
    private final String seatNumber;
    
    public SeatReleasedEvent(Object source, String flightId, String seatNumber) {
        super(source);
        this.flightId = flightId;
        this.seatNumber = seatNumber;
    }
}
