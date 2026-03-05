package com.skyhigh.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FlightListResponseDTO {
    private List<FlightDTO> flights;
    private Integer total;
}
