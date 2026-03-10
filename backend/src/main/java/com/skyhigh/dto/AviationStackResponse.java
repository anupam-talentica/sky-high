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
public class AviationStackResponse {

    private List<FlightData> data;

    private ApiError error;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FlightData {

        @JsonProperty("flight_date")
        private String flightDate;

        @JsonProperty("flight_status")
        private String flightStatus;

        @JsonProperty("flight")
        private FlightInfo flight;

        @JsonProperty("departure")
        private FlightEndpoint departure;

        @JsonProperty("arrival")
        private FlightEndpoint arrival;

        @JsonProperty("delay")
        private Integer delay;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FlightInfo {
        @JsonProperty("iata")
        private String iata;
        @JsonProperty("icao")
        private String icao;
        @JsonProperty("number")
        private String number;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FlightEndpoint {
        @JsonProperty("airport")
        private String airport;

        @JsonProperty("iata")
        private String iata;

        @JsonProperty("terminal")
        private String terminal;

        @JsonProperty("gate")
        private String gate;

        @JsonProperty("scheduled")
        private String scheduled;

        @JsonProperty("estimated")
        private String estimated;

        @JsonProperty("actual")
        private String actual;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ApiError {
        private String code;
        private String type;
        private String info;
    }
}

