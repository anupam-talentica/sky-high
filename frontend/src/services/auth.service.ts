import { axiosInstance, apiClient } from './api.client';
import type { LoginRequest, LoginResponse } from '../types/auth.types';

export const authService = {
  async login(credentials: LoginRequest): Promise<LoginResponse> {
    const response = await axiosInstance.post<LoginResponse>('/auth/login', credentials);
    apiClient.setToken(response.data.token);
    return response.data;
  },

  logout(): void {
    apiClient.removeToken();
    window.location.href = '/login';
  },

  isAuthenticated(): boolean {
    const token = localStorage.getItem('auth_token');
    return !!token;
  },

  getToken(): string | null {
    return localStorage.getItem('auth_token');
  },
};
