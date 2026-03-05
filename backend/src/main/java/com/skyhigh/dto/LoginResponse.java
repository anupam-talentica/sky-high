package com.skyhigh.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for login response containing JWT token and passenger info.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginResponse {

    private String token;
    
    @Builder.Default
    private String tokenType = "Bearer";
    
    private String passengerId;
    
    private String email;
    
    private String name;
}
