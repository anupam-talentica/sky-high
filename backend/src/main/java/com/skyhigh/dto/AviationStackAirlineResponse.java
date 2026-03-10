package com.skyhigh.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AviationStackAirlineResponse {

    private List<AirlineData> data;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AirlineData {

        @JsonProperty("airline_name")
        private String name;

        @JsonProperty("iata_code")
        private String iataCode;

        @JsonProperty("icao_code")
        private String icaoCode;

        @JsonProperty("callsign")
        private String callsign;

        @JsonProperty("country_name")
        private String countryName;

        @JsonProperty("country_iso2")
        private String countryIso2;

        @JsonProperty("website")
        private String website;

        @JsonProperty("phone")
        private String phone;
    }
}

