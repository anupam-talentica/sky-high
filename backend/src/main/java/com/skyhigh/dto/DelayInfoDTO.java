package com.skyhigh.dto;

import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DelayInfoDTO {

    @Min(0)
    private Integer durationMinutes;

    private String reason;
}

