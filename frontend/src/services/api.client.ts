import axios, { AxiosError } from 'axios';
import type { AxiosInstance, InternalAxiosRequestConfig } from 'axios';
import type { ApiError } from '../types/api.types';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api/v1';

class ApiClient {
  private client: AxiosInstance;

  constructor() {
    this.client = axios.create({
      baseURL: API_BASE_URL,
      timeout: 30000,
      headers: {
        'Content-Type': 'application/json',
      },
    });

    this.setupInterceptors();
  }

  private setupInterceptors(): void {
    this.client.interceptors.request.use(
      (config: InternalAxiosRequestConfig) => {
        const token = this.getToken();
        if (token && config.headers) {
          config.headers.Authorization = `Bearer ${token}`;
        }
        return config;
      },
      (error: AxiosError) => {
        return Promise.reject(error);
      }
    );

    this.client.interceptors.response.use(
      (response) => {
        return response;
      },
      (error: AxiosError<ApiError>) => {
        if (error.response) {
          const apiError: ApiError = {
            status: error.response.status,
            message: error.response.data?.message || 'An error occurred',
            timestamp: error.response.data?.timestamp || new Date().toISOString(),
            path: error.response.data?.path || error.config?.url || '',
            errors: error.response.data?.errors,
          };

          if (error.response.status === 401) {
            this.handleUnauthorized();
          }

          return Promise.reject(apiError);
        } else if (error.request) {
          const networkError: ApiError = {
            status: 0,
            message: 'Network error. Please check your connection.',
            timestamp: new Date().toISOString(),
            path: error.config?.url || '',
          };
          return Promise.reject(networkError);
        } else {
          const unknownError: ApiError = {
            status: 0,
            message: error.message || 'An unknown error occurred',
            timestamp: new Date().toISOString(),
            path: '',
          };
          return Promise.reject(unknownError);
        }
      }
    );
  }

  private getToken(): string | null {
    return localStorage.getItem('auth_token');
  }

  private handleUnauthorized(): void {
    localStorage.removeItem('auth_token');
    localStorage.removeItem('auth_state');
    
    if (window.location.pathname !== '/login') {
      window.location.href = '/login';
    }
  }

  public getInstance(): AxiosInstance {
    return this.client;
  }

  public setToken(token: string): void {
    localStorage.setItem('auth_token', token);
  }

  public removeToken(): void {
    localStorage.removeItem('auth_token');
    localStorage.removeItem('auth_state');
  }
}

export const apiClient = new ApiClient();
export const axiosInstance = apiClient.getInstance();
