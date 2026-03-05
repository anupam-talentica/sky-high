import { axiosInstance } from './api.client';
import type {
  CheckIn,
  CheckInInitiateRequest,
  CheckInInitiateResponse,
  CheckInDetailsResponse,
  AddBaggageRequest,
  AddBaggageResponse,
  ConfirmCheckInResponse,
} from '../types/checkin.types';

export const checkinService = {
  async initiateCheckIn(request: CheckInInitiateRequest): Promise<CheckInInitiateResponse> {
    const response = await axiosInstance.post<CheckInInitiateResponse>('/check-ins/initiate', request);
    return response.data;
  },

  async getCheckInDetails(checkInId: string): Promise<CheckInDetailsResponse> {
    const response = await axiosInstance.get<CheckInDetailsResponse>(`/check-ins/${checkInId}`);
    return response.data;
  },

  async selectSeat(checkInId: string, seatId: number, passengerId: string): Promise<CheckIn> {
    const response = await axiosInstance.post<CheckIn>(`/check-ins/${checkInId}/select-seat`, {
      seatId,
      passengerId,
    });
    return response.data;
  },

  async addBaggage(checkInId: string, request: AddBaggageRequest): Promise<AddBaggageResponse> {
    const response = await axiosInstance.post<AddBaggageResponse>(
      `/check-ins/${checkInId}/baggage`,
      request
    );
    return response.data;
  },

  async getBaggage(checkInId: string): Promise<AddBaggageResponse[]> {
    const response = await axiosInstance.get<AddBaggageResponse[]>(
      `/check-ins/${checkInId}/baggage`
    );
    return response.data;
  },

  async deleteBaggage(checkInId: string, baggageId: number): Promise<void> {
    await axiosInstance.delete(`/check-ins/${checkInId}/baggage/${baggageId}`);
  },

  async processPayment(
    checkInId: string,
    paymentData: {
      amount: number;
      paymentMethod: string;
      cardNumber?: string;
      cardHolderName?: string;
    }
  ): Promise<void> {
    await axiosInstance.post(`/check-ins/${checkInId}/payment`, paymentData);
  },

  async confirmCheckIn(checkInId: string): Promise<ConfirmCheckInResponse> {
    const response = await axiosInstance.post<ConfirmCheckInResponse>(
      `/check-ins/${checkInId}/confirm`
    );
    return response.data;
  },

  async cancelCheckIn(checkInId: string): Promise<void> {
    await axiosInstance.post(`/check-ins/${checkInId}/cancel`);
  },

  async getPassengerCheckIns(passengerId: string): Promise<CheckIn[]> {
    const response = await axiosInstance.get<CheckIn[]>(`/check-ins/passenger/${passengerId}`);
    return response.data;
  },
};
