package com.skyhigh.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for JwtTokenProvider.
 */
class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;

    private static final String TEST_SECRET = "test-secret-key-for-jwt-token-generation-must-be-at-least-256-bits-long";
    private static final long TEST_EXPIRATION = 3600000L; // 1 hour

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtSecret", TEST_SECRET);
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtExpirationMs", TEST_EXPIRATION);
    }

    @Test
    void testGenerateToken_Success() {
        String passengerId = "P123456";
        String email = "john@example.com";

        String token = jwtTokenProvider.generateToken(passengerId, email);

        assertNotNull(token);
        assertFalse(token.isEmpty());
    }

    @Test
    void testGetPassengerIdFromToken_Success() {
        String passengerId = "P123456";
        String email = "john@example.com";

        String token = jwtTokenProvider.generateToken(passengerId, email);
        String extractedPassengerId = jwtTokenProvider.getPassengerIdFromToken(token);

        assertEquals(passengerId, extractedPassengerId);
    }

    @Test
    void testGetEmailFromToken_Success() {
        String passengerId = "P123456";
        String email = "john@example.com";

        String token = jwtTokenProvider.generateToken(passengerId, email);
        String extractedEmail = jwtTokenProvider.getEmailFromToken(token);

        assertEquals(email, extractedEmail);
    }

    @Test
    void testValidateToken_ValidToken_ReturnsTrue() {
        String passengerId = "P123456";
        String email = "john@example.com";

        String token = jwtTokenProvider.generateToken(passengerId, email);
        boolean isValid = jwtTokenProvider.validateToken(token);

        assertTrue(isValid);
    }

    @Test
    void testValidateToken_InvalidToken_ReturnsFalse() {
        String invalidToken = "invalid.token.here";

        boolean isValid = jwtTokenProvider.validateToken(invalidToken);

        assertFalse(isValid);
    }

    @Test
    void testValidateToken_MalformedToken_ReturnsFalse() {
        String malformedToken = "not-a-jwt-token";

        boolean isValid = jwtTokenProvider.validateToken(malformedToken);

        assertFalse(isValid);
    }

    @Test
    void testValidateToken_EmptyToken_ReturnsFalse() {
        String emptyToken = "";

        boolean isValid = jwtTokenProvider.validateToken(emptyToken);

        assertFalse(isValid);
    }

    @Test
    void testValidateToken_ExpiredToken_ReturnsFalse() throws InterruptedException {
        JwtTokenProvider shortExpirationProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(shortExpirationProvider, "jwtSecret", TEST_SECRET);
        ReflectionTestUtils.setField(shortExpirationProvider, "jwtExpirationMs", 1L); // 1ms expiration

        String token = shortExpirationProvider.generateToken("P123456", "john@example.com");
        
        Thread.sleep(10); // Wait for token to expire

        boolean isValid = shortExpirationProvider.validateToken(token);

        assertFalse(isValid);
    }
}
