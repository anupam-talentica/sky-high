package com.skyhigh.service;

import com.skyhigh.dto.LoginRequest;
import com.skyhigh.dto.LoginResponse;
import com.skyhigh.exception.AuthenticationFailedException;
import com.skyhigh.security.JwtTokenProvider;
import com.skyhigh.security.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AuthenticationService.
 */
@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    @Mock
    private UserService userService;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    private AuthenticationService authenticationService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .passengerId("P123456")
                .email("john@example.com")
                .password("hashedPassword")
                .name("John Doe")
                .build();
    }

    @Test
    void testLogin_ValidCredentials_ReturnsLoginResponse() {
        LoginRequest loginRequest = new LoginRequest("john@example.com", "demo123");
        String expectedToken = "jwt.token.here";

        when(userService.findByEmail(loginRequest.getEmail()))
                .thenReturn(Optional.of(testUser));
        when(jwtTokenProvider.generateToken(testUser.getPassengerId(), testUser.getEmail()))
                .thenReturn(expectedToken);

        LoginResponse response = authenticationService.login(loginRequest);

        assertNotNull(response);
        assertEquals(expectedToken, response.getToken());
        assertEquals("Bearer", response.getTokenType());
        assertEquals("P123456", response.getPassengerId());
        assertEquals("john@example.com", response.getEmail());
        assertEquals("John Doe", response.getName());

        verify(userService).findByEmail(loginRequest.getEmail());
        verify(jwtTokenProvider).generateToken(testUser.getPassengerId(), testUser.getEmail());
    }

    @Test
    void testLogin_InvalidCredentials_ThrowsAuthenticationFailedException() {
        LoginRequest loginRequest = new LoginRequest("john@example.com", "wrongpassword");

        when(userService.findByEmail(loginRequest.getEmail()))
                .thenReturn(Optional.empty());

        AuthenticationFailedException exception = assertThrows(
                AuthenticationFailedException.class,
                () -> authenticationService.login(loginRequest)
        );

        assertEquals("Invalid email", exception.getMessage());
        verify(userService).findByEmail(loginRequest.getEmail());
        verify(jwtTokenProvider, never()).generateToken(anyString(), anyString());
    }

    @Test
    void testLogin_NonExistentUser_ThrowsAuthenticationFailedException() {
        LoginRequest loginRequest = new LoginRequest("nonexistent@example.com", "demo123");

        when(userService.findByEmail(loginRequest.getEmail()))
                .thenReturn(Optional.empty());

        AuthenticationFailedException exception = assertThrows(
                AuthenticationFailedException.class,
                () -> authenticationService.login(loginRequest)
        );

        assertEquals("Invalid email", exception.getMessage());
        verify(userService).findByEmail(loginRequest.getEmail());
        verify(jwtTokenProvider, never()).generateToken(anyString(), anyString());
    }
}
