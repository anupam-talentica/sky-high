export interface LoginRequest {
  email: string;
  password: string;
}

export interface LoginResponse {
  token: string;
  passengerId: string;
  email: string;
  name: string;
  expiresIn: number;
}

export interface AuthState {
  token: string | null;
  passengerId: string | null;
  email: string | null;
  name: string | null;
  isAuthenticated: boolean;
}
