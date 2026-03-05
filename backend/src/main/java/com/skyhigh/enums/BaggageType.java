package com.skyhigh.enums;

public enum BaggageType {
    CARRY_ON("carry_on"),
    CHECKED("checked"),
    OVERSIZED("oversized");

    private final String value;

    BaggageType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static BaggageType fromValue(String value) {
        for (BaggageType type : BaggageType.values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Invalid baggage type: " + value);
    }
}
