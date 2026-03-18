package com.skyhigh.service.impl;

import com.skyhigh.config.WeatherApiConfig;
import com.skyhigh.dto.AirportDTO;
import com.skyhigh.dto.WeatherApiResponse;
import com.skyhigh.service.WeatherService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

@Service
@Slf4j
public class WeatherServiceImpl implements WeatherService {

    private final RestTemplate weatherApiRestTemplate;
    private final WeatherApiConfig config;
    private final Counter weatherCalls;
    private final Timer weatherTimer;

    public WeatherServiceImpl(
            RestTemplate weatherApiRestTemplate,
            WeatherApiConfig config,
            MeterRegistry meterRegistry) {
        this.weatherApiRestTemplate = weatherApiRestTemplate;
        this.config = config;
        this.weatherCalls = meterRegistry.counter("weatherapi.api.calls.current");
        this.weatherTimer = meterRegistry.timer("weatherapi.api.response.time.current");
    }

    @Override
    public AirportDTO enrichWithCurrentWeather(AirportDTO airport) {
        if (airport == null) {
            return null;
        }

        if (!Boolean.TRUE.equals(config.getEnabled())) {
            return airport;
        }

        // Prefer coordinates when available; fall back to city name.
        String query = buildQueryFromAirport(airport);
        if (!StringUtils.hasText(query)) {
            return airport;
        }

        weatherCalls.increment();

        return weatherTimer.record(() -> {
            try {
                String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
                String url = String.format("%s/current.json?key=%s&q=%s&aqi=no",
                        config.getBaseUrl(), config.getApiKey(), encodedQuery);

                log.info("Calling WeatherAPI for airport {} with query {}: {}", airport.getIataCode(), query, url);

                WeatherApiResponse response = weatherApiRestTemplate.getForObject(url, WeatherApiResponse.class);

                if (response == null || response.getCurrent() == null) {
                    log.warn("WeatherAPI returned no current weather data for query {}", query);
                    return airport;
                }

                var current = response.getCurrent();
                airport.setTemperatureC(current.getTempCelsius());

                String description = Optional.ofNullable(current.getCondition())
                        .map(WeatherApiResponse.Condition::getText)
                        .orElse(null);
                airport.setWeatherDescription(description);

                return airport;
            } catch (RestClientException ex) {
                log.error("Failed to fetch weather data from WeatherAPI for airport {}", airport.getIataCode(), ex);
                // Fail open – return airport details without weather.
                return airport;
            }
        });
    }

    private String buildQueryFromAirport(AirportDTO airport) {
        if (airport.getLatitude() != null && airport.getLongitude() != null) {
            return airport.getLatitude() + "," + airport.getLongitude();
        }

        if (StringUtils.hasText(airport.getCity())) {
            return airport.getCity();
        }

        // As a last resort, try timezone-based query if supported by the API (q accepts flexible formats).
        if (StringUtils.hasText(airport.getTimezone())) {
            return airport.getTimezone();
        }

        return null;
    }
}

