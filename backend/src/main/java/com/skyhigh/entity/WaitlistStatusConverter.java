package com.skyhigh.entity;

import com.skyhigh.enums.WaitlistStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class WaitlistStatusConverter implements AttributeConverter<WaitlistStatus, String> {

    @Override
    public String convertToDatabaseColumn(WaitlistStatus attribute) {
        if (attribute == null) {
            return null;
        }
        return attribute.getValue();
    }

    @Override
    public WaitlistStatus convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        return WaitlistStatus.fromValue(dbData);
    }
}

