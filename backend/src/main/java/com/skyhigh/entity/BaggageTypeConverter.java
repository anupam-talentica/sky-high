package com.skyhigh.entity;

import com.skyhigh.enums.BaggageType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class BaggageTypeConverter implements AttributeConverter<BaggageType, String> {

    @Override
    public String convertToDatabaseColumn(BaggageType attribute) {
        if (attribute == null) {
            return null;
        }
        return attribute.getValue();
    }

    @Override
    public BaggageType convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        return BaggageType.fromValue(dbData);
    }
}
