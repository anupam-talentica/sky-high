package com.skyhigh.service.impl;

import com.skyhigh.config.AviationStackConfig;
import com.skyhigh.dto.AirlineDTO;
import com.skyhigh.dto.AirportDTO;
import com.skyhigh.dto.AviationStackAirlineResponse;
import com.skyhigh.dto.AviationStackAirportResponse;
import com.skyhigh.exception.ExternalServiceException;
import com.skyhigh.service.AviationMetadataService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Service
@Slf4j
public class AviationMetadataServiceImpl implements AviationMetadataService {

    private final RestTemplate restTemplate;
    private final AviationStackConfig config;
    private final Counter airlineCalls;
    private final Counter airportCalls;
    private final Timer airlineTimer;
    private final Timer airportTimer;

    public AviationMetadataServiceImpl(
            RestTemplate aviationStackRestTemplate,
            AviationStackConfig config,
            MeterRegistry meterRegistry) {
        this.restTemplate = aviationStackRestTemplate;
        this.config = config;
        this.airlineCalls = meterRegistry.counter("aviationstack.api.calls.airlines");
        this.airportCalls = meterRegistry.counter("aviationstack.api.calls.airports");
        this.airlineTimer = meterRegistry.timer("aviationstack.api.response.time.airlines");
        this.airportTimer = meterRegistry.timer("aviationstack.api.response.time.airports");
    }

    @Override
    @Cacheable(value = "aviationAirline", key = "#iataCode", unless = "#result == null")
    public AirlineDTO getAirlineByIata(String iataCode) {
        if (!Boolean.TRUE.equals(config.getEnabled())) {
            throw new ExternalServiceException("AviationStack integration is disabled");
        }

        if (!StringUtils.hasText(iataCode)) {
            throw new IllegalArgumentException("IATA code is required");
        }

        airlineCalls.increment();

        return airlineTimer.record(() -> {
            String encoded = URLEncoder.encode(iataCode, StandardCharsets.UTF_8);
            String url = String.format("%s/airlines?iata_code=%s&access_key=%s",
                    config.getBaseUrl(), encoded, config.getApiKey());

            log.info("Calling AviationStack airlines API for IATA code {}: {}", iataCode, url);

            try {
                AviationStackAirlineResponse response =
                        restTemplate.getForObject(url, AviationStackAirlineResponse.class);

                if (response == null || CollectionUtils.isEmpty(response.getData())) {
                    throw new ExternalServiceException("Airline not found for IATA code: " + iataCode);
                }

                var data = response.getData().get(0);

                return AirlineDTO.builder()
                        .name(data.getName())
                        .iataCode(data.getIataCode())
                        .icaoCode(data.getIcaoCode())
                        .callsign(data.getCallsign())
                        .countryName(data.getCountryName())
                        .countryIso2(data.getCountryIso2())
                        .website(data.getWebsite())
                        .phone(data.getPhone())
                        .build();
            } catch (RestClientException ex) {
                log.error("Failed to fetch airline metadata from AviationStack for {}", iataCode, ex);
                throw new ExternalServiceException("Failed to fetch airline metadata", ex);
            }
        });
    }

    @Override
    @Cacheable(value = "aviationAirport", key = "#iataCode", unless = "#result == null")
    public AirportDTO getAirportByIata(String iataCode) {
        if (!Boolean.TRUE.equals(config.getEnabled())) {
            throw new ExternalServiceException("AviationStack integration is disabled");
        }

        if (!StringUtils.hasText(iataCode)) {
            throw new IllegalArgumentException("IATA code is required");
        }

        airportCalls.increment();

        return airportTimer.record(() -> {
            String encoded = URLEncoder.encode(iataCode, StandardCharsets.UTF_8);
            String url = String.format("%s/airports?iata_code=%s&access_key=%s",
                    config.getBaseUrl(), encoded, config.getApiKey());

            log.info("Calling AviationStack airports API for IATA code {}: {}", iataCode, url);

            try {
                AviationStackAirportResponse response =
                        restTemplate.getForObject(url, AviationStackAirportResponse.class);

                if (response == null || CollectionUtils.isEmpty(response.getData())) {
                    throw new ExternalServiceException("Airport not found for IATA code: " + iataCode);
                }

                var data = response.getData().get(0);

                return AirportDTO.builder()
                        .name(data.getName())
                        .iataCode(data.getIataCode())
                        .icaoCode(data.getIcaoCode())
                        .city(data.getCity())
                        .countryName(data.getCountryName())
                        .countryIso2(data.getCountryIso2())
                        .timezone(data.getTimezone())
                        .latitude(data.getLatitude())
                        .longitude(data.getLongitude())
                        .build();
            } catch (RestClientException ex) {
                log.error("Failed to fetch airport metadata from AviationStack for {}", iataCode, ex);
                throw new ExternalServiceException("Failed to fetch airport metadata", ex);
            }
        });
    }
}

