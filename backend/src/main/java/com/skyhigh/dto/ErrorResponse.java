package com.skyhigh.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for error responses.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ErrorResponse {

    private int status;
    
    private String error;
    
    private String message;
    
    private String path;
    
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
}
