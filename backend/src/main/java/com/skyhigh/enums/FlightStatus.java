package com.skyhigh.enums;

public enum FlightStatus {
    SCHEDULED("scheduled"),
    BOARDING("boarding"),
    DEPARTED("departed"),
    ARRIVED("arrived"),
    CANCELLED("cancelled");

    private final String value;

    FlightStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static FlightStatus fromValue(String value) {
        for (FlightStatus status : FlightStatus.values()) {
            if (status.value.equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Invalid flight status: " + value);
    }
}
