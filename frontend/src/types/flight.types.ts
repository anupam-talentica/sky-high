export interface Flight {
  flightId: string;
  flightNumber: string;
  origin: string;
  destination: string;
  departureTime: string;
  arrivalTime: string;
  status: string;
  aircraftType: string;
  totalSeats: number;
  availableSeats: number;
}

export interface FlightListResponse {
  flights: Flight[];
  total: number;
}
