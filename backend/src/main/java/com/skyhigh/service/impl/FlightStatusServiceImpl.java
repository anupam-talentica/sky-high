package com.skyhigh.service.impl;

import com.skyhigh.config.AviationStackConfig;
import com.skyhigh.dto.*;
import com.skyhigh.entity.Flight;
import com.skyhigh.enums.FlightStatus;
import com.skyhigh.exception.ExternalServiceException;
import com.skyhigh.exception.FlightNotFoundException;
import com.skyhigh.repository.FlightRepository;
import com.skyhigh.service.FlightStatusService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Optional;

@Service
@Slf4j
public class FlightStatusServiceImpl implements FlightStatusService {

    private final RestTemplate restTemplate;
    private final AviationStackConfig config;
    private final FlightRepository flightRepository;
    private final Counter totalCallsCounter;
    private final Counter successCallsCounter;
    private final Counter failedCallsCounter;
    private final Counter cacheHitCounter;
    private final Counter cacheMissCounter;
    private final Timer responseTimer;

    public FlightStatusServiceImpl(
            RestTemplate aviationStackRestTemplate,
            AviationStackConfig config,
            FlightRepository flightRepository,
            MeterRegistry meterRegistry) {
        this.restTemplate = aviationStackRestTemplate;
        this.config = config;
        this.flightRepository = flightRepository;
        this.totalCallsCounter = meterRegistry.counter("aviationstack.api.calls.total");
        this.successCallsCounter = meterRegistry.counter("aviationstack.api.calls.success");
        this.failedCallsCounter = meterRegistry.counter("aviationstack.api.calls.failure");
        this.cacheHitCounter = meterRegistry.counter("aviationstack.cache.hits");
        this.cacheMissCounter = meterRegistry.counter("aviationstack.cache.misses");
        this.responseTimer = meterRegistry.timer("aviationstack.api.response.time");
    }

    @Override
    @CircuitBreaker(name = "aviationstack", fallbackMethod = "getFallbackFlightStatus")
    @Retry(name = "aviationstack")
    @Cacheable(value = "flightStatus", key = "#flightNumber", unless = "#result == null")
    public FlightStatusDTO getFlightStatus(String flightNumber) {
        cacheMissCounter.increment();
        totalCallsCounter.increment();

        if (Boolean.FALSE.equals(config.getEnabled())) {
            log.info("AviationStack integration disabled. Using fallback for flight '{}'", flightNumber);
            return buildFallbackFromDatabase(flightNumber)
                    .orElseThrow(() -> new FlightNotFoundException("Flight not found: " + flightNumber));
        }

        return responseTimer.record(() -> doGetFlightStatus(flightNumber));
    }

    @Override
    public FlightStatusDTO getFlightStatusWithFallback(String flightNumber) {
        try {
            return getFlightStatus(flightNumber);
        } catch (Exception ex) {
            log.warn("AviationStack API failed for flight '{}', falling back to database: {}",
                    flightNumber, ex.getMessage());
            return buildFallbackFromDatabase(flightNumber)
                    .orElseThrow(() -> new FlightNotFoundException("Flight not found: " + flightNumber));
        }
    }

    @SuppressWarnings("unused")
    private FlightStatusDTO getFallbackFlightStatus(String flightNumber, Throwable cause) {
        failedCallsCounter.increment();
        log.warn("Circuit breaker fallback for flight '{}': {}", flightNumber, cause.getMessage());
        return buildFallbackFromDatabase(flightNumber)
                .orElseThrow(() -> new FlightNotFoundException("Flight not found: " + flightNumber));
    }

    private FlightStatusDTO doGetFlightStatus(String flightNumber) {
        String encodedFlightNumber = URLEncoder.encode(flightNumber, StandardCharsets.UTF_8);
        String url = String.format("%s/flights?flight_iata=%s&access_key=%s",
                config.getBaseUrl(), encodedFlightNumber, config.getApiKey());

        log.info("Calling AviationStack for flight '{}': {}", flightNumber, url);

        try {
            AviationStackResponse response =
                    restTemplate.getForObject(url, AviationStackResponse.class);

            if (response == null) {
                throw new ExternalServiceException("Empty response from AviationStack");
            }

            if (response.getError() != null) {
                AviationStackResponse.ApiError error = response.getError();
                throw new ExternalServiceException(
                        "AviationStack error: " + error.getCode() + " - " + error.getInfo());
            }

            if (response.getData() == null || response.getData().isEmpty()) {
                throw new FlightNotFoundException("Flight not found: " + flightNumber);
            }

            successCallsCounter.increment();

            AviationStackResponse.FlightData flightData = response.getData().get(0);
            FlightStatusDTO dto = mapToFlightStatusDTO(flightData);

            // Try to attach internal flightId if we have a matching record.
            findFlightByNumberOrId(flightNumber).ifPresent(flight -> dto.setFlightId(flight.getFlightId()));

            return dto;
        } catch (RestClientException ex) {
            failedCallsCounter.increment();
            log.error("Failed to fetch flight status from AviationStack for {}", flightNumber, ex);
            throw new ExternalServiceException("AviationStack API unavailable", ex);
        }
    }

    private Optional<Flight> findFlightByNumberOrId(String flightKey) {
        // First try treating the key as our internal flightId.
        Optional<Flight> byId = flightRepository.findById(flightKey);
        if (byId.isPresent()) {
            return byId;
        }
        // Fallback: treat as flight number.
        return flightRepository.findByFlightNumber(flightKey);
    }

    private Optional<FlightStatusDTO> buildFallbackFromDatabase(String flightKey) {
        return findFlightByNumberOrId(flightKey)
                .map(this::mapFlightEntityToDTO);
    }

    private FlightStatusDTO mapFlightEntityToDTO(Flight flight) {
        cacheHitCounter.increment();

        String statusValue = flight.getStatus();
        if (StringUtils.hasText(statusValue)) {
            try {
                statusValue = FlightStatus.fromValue(statusValue).getValue();
            } catch (IllegalArgumentException ignored) {
                // keep original status string if it doesn't map to enum
            }
        }

        DepartureInfoDTO departure = DepartureInfoDTO.builder()
                .airportIata(flight.getDepartureAirport())
                .airportName(flight.getDepartureAirport())
                .scheduledTime(flight.getDepartureTime())
                .build();

        ArrivalInfoDTO arrival = ArrivalInfoDTO.builder()
                .airportIata(flight.getArrivalAirport())
                .airportName(flight.getArrivalAirport())
                .scheduledTime(flight.getArrivalTime())
                .build();

        DelayInfoDTO delay = DelayInfoDTO.builder()
                .durationMinutes(0)
                .reason(null)
                .build();

        return FlightStatusDTO.builder()
                .flightId(flight.getFlightId())
                .flightNumber(flight.getFlightNumber())
                .status(statusValue)
                .departure(departure)
                .arrival(arrival)
                .delay(delay)
                .lastUpdated(LocalDateTime.now())
                .build();
    }

    private FlightStatusDTO mapToFlightStatusDTO(AviationStackResponse.FlightData data) {
        String externalStatus = data.getFlightStatus();
        String normalizedStatus = externalStatus;

        if (StringUtils.hasText(externalStatus)) {
            try {
                normalizedStatus = FlightStatus.fromValue(externalStatus).getValue();
            } catch (IllegalArgumentException ignored) {
                normalizedStatus = externalStatus.toLowerCase();
            }
        }

        DepartureInfoDTO departure = DepartureInfoDTO.builder()
                .airportIata(data.getDeparture() != null ? data.getDeparture().getIata() : null)
                .airportName(data.getDeparture() != null ? data.getDeparture().getAirport() : null)
                .terminal(data.getDeparture() != null ? data.getDeparture().getTerminal() : null)
                .gate(data.getDeparture() != null ? data.getDeparture().getGate() : null)
                .scheduledTime(parseDateTimeSafely(data.getDeparture() != null ? data.getDeparture().getScheduled() : null))
                .estimatedTime(parseDateTimeSafely(data.getDeparture() != null ? data.getDeparture().getEstimated() : null))
                .actualTime(parseDateTimeSafely(data.getDeparture() != null ? data.getDeparture().getActual() : null))
                .build();

        ArrivalInfoDTO arrival = ArrivalInfoDTO.builder()
                .airportIata(data.getArrival() != null ? data.getArrival().getIata() : null)
                .airportName(data.getArrival() != null ? data.getArrival().getAirport() : null)
                .terminal(data.getArrival() != null ? data.getArrival().getTerminal() : null)
                .gate(data.getArrival() != null ? data.getArrival().getGate() : null)
                .scheduledTime(parseDateTimeSafely(data.getArrival() != null ? data.getArrival().getScheduled() : null))
                .estimatedTime(parseDateTimeSafely(data.getArrival() != null ? data.getArrival().getEstimated() : null))
                .actualTime(parseDateTimeSafely(data.getArrival() != null ? data.getArrival().getActual() : null))
                .build();

        DelayInfoDTO delay = DelayInfoDTO.builder()
                .durationMinutes(data.getDelay())
                .reason(null)
                .build();

        String flightNumber = data.getFlight() != null && data.getFlight().getIata() != null
                ? data.getFlight().getIata()
                : (data.getFlight() != null ? data.getFlight().getNumber() : null);

        return FlightStatusDTO.builder()
                .flightId(flightNumber) // will be overridden with internal ID when available
                .flightNumber(flightNumber)
                .status(normalizedStatus)
                .departure(departure)
                .arrival(arrival)
                .delay(delay)
                .lastUpdated(LocalDateTime.now())
                .build();
    }

    private LocalDateTime parseDateTimeSafely(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value).toLocalDateTime();
        } catch (DateTimeParseException ex) {
            log.debug("Failed to parse datetime from AviationStack value '{}': {}", value, ex.getMessage());
            return null;
        }
    }
}

