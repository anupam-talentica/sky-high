package com.skyhigh.entity;

import com.skyhigh.enums.SeatType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class SeatTypeConverter implements AttributeConverter<SeatType, String> {

    @Override
    public String convertToDatabaseColumn(SeatType attribute) {
        if (attribute == null) {
            return null;
        }
        return attribute.name().toLowerCase();
    }

    @Override
    public SeatType convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        return SeatType.valueOf(dbData.toUpperCase());
    }
}
