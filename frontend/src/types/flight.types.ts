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

export interface Airline {
  name: string;
  iataCode: string;
  icaoCode: string;
  callsign: string;
  countryName: string;
  countryIso2: string;
  website?: string;
  phone?: string;
}

export interface Airport {
  name: string;
  iataCode: string;
  icaoCode: string;
  city: string;
  countryName: string;
  countryIso2: string;
  timezone: string;
  latitude?: number;
  longitude?: number;
}
