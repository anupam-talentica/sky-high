package com.skyhigh.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.skyhigh.exception.RateLimitExceededException;
import com.skyhigh.service.SeatMapAbuseDetectionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SeatMapRateLimitingFilterTest {

    private SeatMapAbuseDetectionService abuseDetectionService;
    private RequestSourceResolver requestSourceResolver;
    private SeatMapRateLimitingFilter filter;

    @BeforeEach
    void setUp() {
        abuseDetectionService = Mockito.mock(SeatMapAbuseDetectionService.class);
        requestSourceResolver = Mockito.mock(RequestSourceResolver.class);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        filter = new SeatMapRateLimitingFilter(abuseDetectionService, requestSourceResolver, objectMapper, true);
    }

    @Test
    void doFilterInternal_whenSeatMapRequest_shouldInvokeAbuseDetection() throws ServletException, IOException {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/v1/flights/FL123/seat-map");
        when(requestSourceResolver.resolveSource(any(HttpServletRequest.class))).thenReturn("1.2.3.4");

        filter.doFilterInternal(request, response, chain);

        verify(abuseDetectionService, times(1)).checkSeatMapAccessAllowed("1.2.3.4");
        verify(chain, times(1)).doFilter(request, response);
    }

    @Test
    void doFilterInternal_whenAbuseDetected_shouldShortCircuitAndNotCallChain() throws ServletException, IOException {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        java.io.PrintWriter writer = mock(java.io.PrintWriter.class);

        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/v1/flights/FL123/seat-map");
        when(requestSourceResolver.resolveSource(any(HttpServletRequest.class))).thenReturn("1.2.3.4");
        when(response.getWriter()).thenReturn(writer);

        doThrow(new RateLimitExceededException("Too many requests", 60))
                .when(abuseDetectionService)
                .checkSeatMapAccessAllowed("1.2.3.4");

        filter.doFilterInternal(request, response, chain);

        verify(chain, never()).doFilter(any(HttpServletRequest.class), any(HttpServletResponse.class));
        verify(response).setStatus(429);
    }
}

