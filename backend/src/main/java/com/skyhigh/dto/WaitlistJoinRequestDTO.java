package com.skyhigh.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WaitlistJoinRequestDTO {
    
    @NotBlank(message = "Passenger ID is required")
    private String passengerId;
}
