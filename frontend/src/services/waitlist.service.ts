import { axiosInstance } from './api.client';
import type {
  Waitlist,
  JoinWaitlistRequest,
  JoinWaitlistResponse,
  WaitlistPositionResponse,
} from '../types/waitlist.types';

export const waitlistService = {
  async joinWaitlist(request: JoinWaitlistRequest): Promise<JoinWaitlistResponse> {
    const { passengerId, flightId, seatNumber } = request;
    const response = await axiosInstance.post<JoinWaitlistResponse>(
      `/flights/${flightId}/seats/${seatNumber}/waitlist`,
      { passengerId }
    );
    return response.data;
  },

  async getWaitlistPosition(waitlistId: number): Promise<WaitlistPositionResponse> {
    const response = await axiosInstance.get<WaitlistPositionResponse>(
      `/waitlist/${waitlistId}/position`
    );
    return response.data;
  },

  async leaveWaitlist(waitlistId: number): Promise<void> {
    await axiosInstance.delete(`/waitlist/${waitlistId}`);
  },

  async getPassengerWaitlists(passengerId: string): Promise<Waitlist[]> {
    const response = await axiosInstance.get<Waitlist[]>(`/passengers/${passengerId}/waitlist`);
    return response.data;
  },

  // Reserved for future use if flight-level waitlist views are added in the UI.
  async getFlightWaitlist(flightId: string, seatNumber: string): Promise<Waitlist[]> {
    const response = await axiosInstance.get<Waitlist[]>(
      `/flights/${flightId}/seats/${seatNumber}/waitlist`
    );
    return response.data;
  },
};
