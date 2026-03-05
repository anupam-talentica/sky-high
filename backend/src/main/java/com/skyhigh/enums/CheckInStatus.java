package com.skyhigh.enums;

public enum CheckInStatus {
    PENDING("pending"),
    BAGGAGE_ADDED("baggage_added"),
    PAYMENT_COMPLETED("payment_completed"),
    COMPLETED("completed"),
    CANCELLED("cancelled");

    private final String value;

    CheckInStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static CheckInStatus fromValue(String value) {
        for (CheckInStatus status : CheckInStatus.values()) {
            if (status.value.equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Invalid check-in status: " + value);
    }

    public boolean canTransitionTo(CheckInStatus newStatus) {
        if (this == newStatus) {
            return true;
        }

        switch (this) {
            case PENDING:
                return newStatus == BAGGAGE_ADDED || newStatus == CANCELLED;
            case BAGGAGE_ADDED:
                return newStatus == PAYMENT_COMPLETED || newStatus == CANCELLED || newStatus == PENDING;
            case PAYMENT_COMPLETED:
                return newStatus == COMPLETED || newStatus == CANCELLED;
            case COMPLETED:
                return newStatus == CANCELLED;
            case CANCELLED:
                return false;
            default:
                return false;
        }
    }
}
