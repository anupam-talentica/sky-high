package com.skyhigh.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skyhigh.dto.ErrorResponse;
import com.skyhigh.exception.RateLimitExceededException;
import com.skyhigh.service.SeatMapAbuseDetectionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * HTTP filter that enforces Redis-backed abuse detection / rate limiting for
 * seat map and seat reservation endpoints.
 * <p>
 * Paths protected:
 * - GET  /api/v1/flights/{flightId}/seat-map
 * - GET  /api/v1/seats/flight/{flightId}
 * - POST /api/v1/flights/{flightId}/seats/{seatNumber}/reserve
 * - POST /api/v1/seats/flight/{flightId}/seat/{seatNumber}/reserve
 */
@Component
public class SeatMapRateLimitingFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(SeatMapRateLimitingFilter.class);

    private final SeatMapAbuseDetectionService abuseDetectionService;
    private final RequestSourceResolver requestSourceResolver;
    private final ObjectMapper objectMapper;
    private final boolean enabled;

    public SeatMapRateLimitingFilter(
            SeatMapAbuseDetectionService abuseDetectionService,
            RequestSourceResolver requestSourceResolver,
            ObjectMapper objectMapper,
            @Value("${app.rate-limit.seat-map.enabled:true}") boolean enabled) {
        this.abuseDetectionService = abuseDetectionService;
        this.requestSourceResolver = requestSourceResolver;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        if (!enabled || !isProtectedEndpoint(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String sourceId = requestSourceResolver.resolveSource(request);

        logger.debug("Applying seat map rate limiting for source {} on {} {}", sourceId, request.getMethod(), request.getRequestURI());

        try {
            // May throw RateLimitExceededException when abuse is detected
            abuseDetectionService.checkSeatMapAccessAllowed(sourceId);
        } catch (RateLimitExceededException ex) {
            logger.warn("Rate limit exceeded for source {} on {} {}: {}", sourceId, request.getMethod(), request.getRequestURI(), ex.getMessage());

            if (!response.isCommitted()) {
                response.setStatus(429);
                response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()));
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setCharacterEncoding(StandardCharsets.UTF_8.name());

                ErrorResponse error = ErrorResponse.builder()
                        .status(429)
                        .error("Too Many Requests")
                        .message(ex.getMessage())
                        .path(request.getRequestURI())
                        .build();

                String body = objectMapper.writeValueAsString(error);
                response.getWriter().write(body);
            }

            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isProtectedEndpoint(HttpServletRequest request) {
        String method = request.getMethod();
        String uri = request.getRequestURI();

        // Seat map reads (full map and lightweight status endpoint)
        if ("GET".equalsIgnoreCase(method)) {
            if (uri.startsWith("/api/v1/flights/")
                    && (uri.endsWith("/seat-map") || uri.endsWith("/seat-map/status"))) {
                return true;
            }
            if (uri.startsWith("/api/v1/seats/flight/")) {
                return true;
            }
        }

        // Seat reservations (optional but desirable to protect from abuse)
        if ("POST".equalsIgnoreCase(method)) {
            if (uri.startsWith("/api/v1/flights/") && uri.contains("/seats/") && uri.endsWith("/reserve")) {
                return true;
            }
            if (uri.startsWith("/api/v1/seats/flight/") && uri.contains("/seat/") && uri.endsWith("/reserve")) {
                return true;
            }
        }

        return false;
    }
}

