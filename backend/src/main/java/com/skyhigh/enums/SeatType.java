package com.skyhigh.enums;

public enum SeatType {
    WINDOW("window"),
    MIDDLE("middle"),
    AISLE("aisle");

    private final String value;

    SeatType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static SeatType fromValue(String value) {
        for (SeatType type : SeatType.values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Invalid seat type: " + value);
    }
}
