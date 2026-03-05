package com.skyhigh.enums;

public enum SeatState {
    AVAILABLE,
    HELD,
    CONFIRMED,
    CANCELLED;

    public boolean canTransitionTo(SeatState newState) {
        switch (this) {
            case AVAILABLE:
                return newState == HELD;
            case HELD:
                return newState == CONFIRMED || newState == AVAILABLE;
            case CONFIRMED:
                return newState == CANCELLED;
            case CANCELLED:
                return newState == AVAILABLE;
            default:
                return false;
        }
    }
}
