export const WaitlistStatus = {
  WAITING: 'WAITING',
  NOTIFIED: 'NOTIFIED',
  ASSIGNED: 'ASSIGNED',
  EXPIRED: 'EXPIRED',
  CANCELLED: 'CANCELLED',
} as const;

export type WaitlistStatus = typeof WaitlistStatus[keyof typeof WaitlistStatus];

export interface Waitlist {
  waitlistId: number;
  passengerId: string;
  flightId: string;
  seatNumber: string;
  position: number;
  status: WaitlistStatus;
  joinedAt: string;
  notifiedAt: string | null;
  assignedAt: string | null;
  expiredAt?: string | null;
}

export interface JoinWaitlistRequest {
  passengerId: string;
  flightId: string;
  seatNumber: string;
}

export interface JoinWaitlistResponse {
  waitlistId: number;
  passengerId: string;
  flightId: string;
  seatNumber: string;
  position: number;
  status: WaitlistStatus;
  message: string;
}

export interface WaitlistPositionResponse {
  waitlistId: number;
  position: number;
  status: WaitlistStatus;
  estimatedWaitTime?: string | null;
}
