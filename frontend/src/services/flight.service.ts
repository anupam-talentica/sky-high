import { axiosInstance } from './api.client';
import type { Flight, FlightListResponse } from '../types/flight.types';

export const flightService = {
  async getAllFlights(): Promise<FlightListResponse> {
    const response = await axiosInstance.get<FlightListResponse>('/flights');
    return response.data;
  },

  async getFlightById(flightId: string): Promise<Flight> {
    const response = await axiosInstance.get<Flight>(`/flights/${flightId}`);
    return response.data;
  },

  async searchFlights(origin?: string, destination?: string): Promise<FlightListResponse> {
    const params = new URLSearchParams();
    if (origin) params.append('origin', origin);
    if (destination) params.append('destination', destination);
    
    const response = await axiosInstance.get<FlightListResponse>(`/flights/search?${params}`);
    return response.data;
  },
};
