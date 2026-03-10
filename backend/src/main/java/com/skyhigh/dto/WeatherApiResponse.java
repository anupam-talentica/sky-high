package com.skyhigh.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class WeatherApiResponse {

    @JsonProperty("location")
    private Location location;

    @JsonProperty("current")
    private Current current;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Location {
        @JsonProperty("name")
        private String name;

        @JsonProperty("region")
        private String region;

        @JsonProperty("country")
        private String country;

        /**
         * Timezone identifier, e.g. "America/New_York".
         */
        @JsonProperty("tz_id")
        private String timezoneId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Current {

        @JsonProperty("temp_c")
        private Double tempCelsius;

        @JsonProperty("condition")
        private Condition condition;

        @JsonProperty("feelslike_c")
        private Double feelsLikeCelsius;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Condition {

        @JsonProperty("text")
        private String text;

        @JsonProperty("icon")
        private String iconUrl;
    }
}

