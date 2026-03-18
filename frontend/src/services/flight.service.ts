import { axiosInstance } from './api.client';
import type { Airline, Airport, Flight, FlightListResponse } from '../types/flight.types';

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

  async getAirlineByIata(iataCode: string): Promise<Airline> {
    const response = await axiosInstance.get<Airline>(`/airlines/${iataCode}`);
    return response.data;
  },

  async getAirportByIata(iataCode: string): Promise<Airport> {
    const response = await axiosInstance.get<Airport>(`/airports/${iataCode}`);
    return response.data;
  },

  async getAirportWeatherByIata(iataCode: string): Promise<Airport> {
    const response = await axiosInstance.get<Airport>(`/airports/${iataCode}/weather`);
    return response.data;
  },
};
