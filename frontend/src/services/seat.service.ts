import { axiosInstance } from './api.client';
import type {
  Seat,
  SeatMapResponse,
  SeatReservationRequest,
  SeatReservationResponse,
} from '../types/seat.types';

export const seatService = {
  async getSeatMap(flightId: string): Promise<SeatMapResponse> {
    const response = await axiosInstance.get<SeatMapResponse>(`/seats/flight/${flightId}`);
    return response.data;
  },

  async getSeatById(seatId: number): Promise<Seat> {
    const response = await axiosInstance.get<Seat>(`/seats/${seatId}`);
    return response.data;
  },

  async reserveSeat(
    flightId: string,
    seatNumber: string,
    request: SeatReservationRequest
  ): Promise<SeatReservationResponse> {
    const response = await axiosInstance.post<SeatReservationResponse>(
      `/flights/${flightId}/seats/${seatNumber}/reserve`,
      request
    );
    return response.data;
  },

  async releaseSeat(flightId: string, seatNumber: string): Promise<void> {
    await axiosInstance.post(`/flights/${flightId}/seats/${seatNumber}/release`);
  },

  async confirmSeat(flightId: string, seatNumber: string): Promise<void> {
    await axiosInstance.post(`/flights/${flightId}/seats/${seatNumber}/confirm`);
  },
};
