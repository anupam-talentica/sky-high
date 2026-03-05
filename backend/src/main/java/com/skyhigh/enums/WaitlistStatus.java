package com.skyhigh.enums;

public enum WaitlistStatus {
    WAITING("waiting"),
    NOTIFIED("notified"),
    ASSIGNED("assigned"),
    EXPIRED("expired"),
    CANCELLED("cancelled");

    private final String value;

    WaitlistStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static WaitlistStatus fromValue(String value) {
        for (WaitlistStatus status : WaitlistStatus.values()) {
            if (status.value.equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Invalid waitlist status: " + value);
    }
}
