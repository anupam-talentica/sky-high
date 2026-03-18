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
public class AviationStackAirportResponse {

    private List<AirportData> data;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AirportData {

        @JsonProperty("airport_name")
        private String name;

        @JsonProperty("iata_code")
        private String iataCode;

        @JsonProperty("icao_code")
        private String icaoCode;

        @JsonProperty("city")
        private String city;

        @JsonProperty("country_name")
        private String countryName;

        @JsonProperty("country_iso2")
        private String countryIso2;

        @JsonProperty("timezone")
        private String timezone;

        @JsonProperty("latitude")
        private Double latitude;

        @JsonProperty("longitude")
        private Double longitude;
    }
}

