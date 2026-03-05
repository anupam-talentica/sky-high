package com.skyhigh.entity;

import com.skyhigh.enums.CheckInStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class CheckInStatusConverter implements AttributeConverter<CheckInStatus, String> {

    @Override
    public String convertToDatabaseColumn(CheckInStatus attribute) {
        if (attribute == null) {
            return null;
        }
        return attribute.name().toLowerCase();
    }

    @Override
    public CheckInStatus convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        return CheckInStatus.valueOf(dbData.toUpperCase());
    }
}
