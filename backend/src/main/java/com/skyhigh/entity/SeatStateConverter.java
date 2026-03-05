package com.skyhigh.entity;

import com.skyhigh.enums.SeatState;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class SeatStateConverter implements AttributeConverter<SeatState, String> {

    @Override
    public String convertToDatabaseColumn(SeatState attribute) {
        if (attribute == null) {
            return null;
        }
        return attribute.name();
    }

    @Override
    public SeatState convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        return SeatState.valueOf(dbData.toUpperCase());
    }
}
