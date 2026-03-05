export const CheckInStatus = {
  PENDING: 'PENDING',
  BAGGAGE_ADDED: 'BAGGAGE_ADDED',
  PAYMENT_COMPLETED: 'PAYMENT_COMPLETED',
  COMPLETED: 'COMPLETED',
  CANCELLED: 'CANCELLED',
} as const;

export type CheckInStatus = typeof CheckInStatus[keyof typeof CheckInStatus];

export interface CheckIn {
  checkInId: string;
  passengerId: string;
  flightId: string;
  seatId: number | null;
  status: CheckInStatus;
  initiatedAt: string;
  completedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CheckInInitiateRequest {
  passengerId: string;
  flightId: string;
}

export interface CheckInInitiateResponse {
  checkInId: string;
  passengerId: string;
  flightId: string;
  status: CheckInStatus;
  message: string;
}

export interface CheckInDetailsResponse {
  checkInId: string;
  passengerId: string;
  passengerName?: string;
  flightId: string;
  seatId: number | null;
  seatNumber: string | null;
  status: CheckInStatus;
  checkInTime?: string;
  completedAt: string | null;
  cancelledAt?: string | null;
  createdAt?: string;
  updatedAt?: string;
  message?: string;
  baggageDetails?: AddBaggageResponse[];
  totalBaggageFee?: number;
  totalFee?: number;
  boardingPass?: string;
  initiatedAt?: string;
}

export interface BaggageDetails {
  baggageId: number;
  weight: number;
  unit: string;
  type: 'carry_on' | 'checked';
  fee: number;
}

export interface AddBaggageRequest {
  weightKg: number;
  dimensions?: string;
  baggageType: 'CARRY_ON' | 'CHECKED' | 'OVERSIZED';
}

export interface AddBaggageResponse {
  baggageId: number;
  checkInId: string;
  weightKg: number;
  dimensions?: string;
  baggageType: 'CARRY_ON' | 'CHECKED' | 'OVERSIZED';
  excessWeightKg: number;
  excessFee: number;
  paymentStatus: string;
  paymentTransactionId?: string;
  message?: string;
}

export interface ConfirmCheckInResponse {
  checkInId: string;
  status: CheckInStatus;
  seatNumber: string;
  boardingPass: string;
  message: string;
}
