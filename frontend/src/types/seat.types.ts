export const SeatState = {
  AVAILABLE: 'AVAILABLE',
  HELD: 'HELD',
  CONFIRMED: 'CONFIRMED',
  CANCELLED: 'CANCELLED',
} as const;

export type SeatState = typeof SeatState[keyof typeof SeatState];

export const SeatType = {
  WINDOW: 'WINDOW',
  AISLE: 'AISLE',
  MIDDLE: 'MIDDLE',
} as const;

export type SeatType = typeof SeatType[keyof typeof SeatType];

export interface Seat {
  seatId: number;
  seatNumber: string;
  seatType: SeatType;
  state: SeatState;
  available: boolean;
  heldBy: string | null;
  confirmedBy: string | null;
  // Optional fields not returned by backend
  flightId?: string;
  price?: number;
  amenities?: string[];
  heldUntil?: string | null;
  version?: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface SeatReservationRequest {
  passengerId: string;
}

export interface SeatReservationResponse {
  seatId: number;
  seatNumber: string;
  state: SeatState;
  heldUntil: string;
  message: string;
}

export interface SeatMapResponse {
  flightId: string;
  seats: Seat[];
  totalSeats: number;
  availableSeats: number;
  heldSeats?: number;
  confirmedSeats?: number;
}
