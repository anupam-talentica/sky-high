package com.skyhigh.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AirlineDTO {

    private String name;
    private String iataCode;
    private String icaoCode;
    private String callsign;
    private String countryName;
    private String countryIso2;
    private String website;
    private String phone;
}

