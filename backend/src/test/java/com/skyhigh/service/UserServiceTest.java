package com.skyhigh.service;

import com.skyhigh.entity.Passenger;
import com.skyhigh.repository.PassengerRepository;
import com.skyhigh.security.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for UserService.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserServiceTest {

    @Mock
    private PassengerRepository passengerRepository;

    @InjectMocks
    private UserService userService;
    
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        ReflectionTestUtils.setField(userService, "passwordEncoder", passwordEncoder);
        
        Passenger john = new Passenger();
        john.setPassengerId("P123456");
        john.setFirstName("John");
        john.setLastName("Doe");
        john.setEmail("john@example.com");
        john.setPasswordHash(passwordEncoder.encode("demo123"));
        
        Passenger jane = new Passenger();
        jane.setPassengerId("P789012");
        jane.setFirstName("Jane");
        jane.setLastName("Smith");
        jane.setEmail("jane@example.com");
        jane.setPasswordHash(passwordEncoder.encode("demo456"));
        
        when(passengerRepository.findByEmail("john@example.com")).thenReturn(Optional.of(john));
        when(passengerRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(jane));
        when(passengerRepository.findByEmail("nonexistent@example.com")).thenReturn(Optional.empty());
        when(passengerRepository.findByEmail("wrong@example.com")).thenReturn(Optional.empty());
    }

    @Test
    void testFindByEmail_ExistingUser_ReturnsUser() {
        Optional<User> user = userService.findByEmail("john@example.com");

        assertTrue(user.isPresent());
        assertEquals("P123456", user.get().getPassengerId());
        assertEquals("john@example.com", user.get().getEmail());
        assertEquals("John Doe", user.get().getName());
    }

    @Test
    void testFindByEmail_NonExistingUser_ReturnsEmpty() {
        Optional<User> user = userService.findByEmail("nonexistent@example.com");

        assertFalse(user.isPresent());
    }

    @Test
    void testValidateCredentials_ValidCredentials_ReturnsUser() {
        Optional<User> user = userService.validateCredentials("john@example.com", "demo123");

        assertTrue(user.isPresent());
        assertEquals("P123456", user.get().getPassengerId());
    }

    @Test
    void testValidateCredentials_InvalidPassword_ReturnsEmpty() {
        Passenger john = new Passenger();
        john.setPassengerId("P123456");
        john.setFirstName("John");
        john.setLastName("Doe");
        john.setEmail("john@example.com");
        john.setPasswordHash(passwordEncoder.encode("demo123"));
        
        when(passengerRepository.findByEmail("john@example.com")).thenReturn(Optional.of(john));
        
        Optional<User> user = userService.validateCredentials("john@example.com", "wrongpassword");

        assertFalse(user.isPresent());
    }

    @Test
    void testValidateCredentials_InvalidEmail_ReturnsEmpty() {
        Optional<User> user = userService.validateCredentials("wrong@example.com", "demo123");

        assertFalse(user.isPresent());
    }

    @Test
    void testInit_CreatesMultipleUsers() {
        Optional<User> user1 = userService.findByEmail("john@example.com");
        Optional<User> user2 = userService.findByEmail("jane@example.com");

        assertTrue(user1.isPresent());
        assertTrue(user2.isPresent());
        assertEquals("P123456", user1.get().getPassengerId());
        assertEquals("P789012", user2.get().getPassengerId());
    }

    @Test
    void testPasswordsAreHashed() {
        Optional<User> user = userService.findByEmail("john@example.com");

        assertTrue(user.isPresent());
        assertNotEquals("demo123", user.get().getPassword());
        assertTrue(passwordEncoder.matches("demo123", user.get().getPassword()));
    }
}
