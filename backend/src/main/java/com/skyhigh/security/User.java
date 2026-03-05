package com.skyhigh.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Simple User model for hardcoded authentication (MVP).
 * In production, this would be a JPA entity stored in database.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    private String passengerId;
    
    private String email;
    
    private String password; // BCrypt hashed
    
    private String name;
}
