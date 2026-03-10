package com.skyhigh.service;

import com.skyhigh.dto.AirlineDTO;
import com.skyhigh.dto.AirportDTO;

public interface AviationMetadataService {

    AirlineDTO getAirlineByIata(String iataCode);

    AirportDTO getAirportByIata(String iataCode);
}

