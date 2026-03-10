package com.skyhigh.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArrivalInfoDTO {

    @NotBlank
    @Size(max = 3)
    private String airportIata;

    @NotBlank
    private String airportName;

    private String terminal;

    private String gate;

    private LocalDateTime scheduledTime;

    private LocalDateTime estimatedTime;

    private LocalDateTime actualTime;
}

