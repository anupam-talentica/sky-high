package com.skyhigh.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AirportDTO {

    private String name;
    private String iataCode;
    private String icaoCode;
    private String city;
    private String countryName;
    private String countryIso2;
    private String timezone;
    private Double latitude;
    private Double longitude;

    /**
     * Current temperature at the airport location in degrees Celsius.
     */
    private Double temperatureC;

    /**
     * Short textual description of the current weather conditions.
     */
    private String weatherDescription;
}

