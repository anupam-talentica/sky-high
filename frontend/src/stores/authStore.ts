import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import type { AuthState, LoginResponse } from '../types/auth.types';

interface AuthStore extends AuthState {
  setAuth: (data: LoginResponse) => void;
  clearAuth: () => void;
  updateToken: (token: string) => void;
}

const initialState: AuthState = {
  token: null,
  passengerId: null,
  email: null,
  name: null,
  isAuthenticated: false,
};

export const useAuthStore = create<AuthStore>()(
  persist(
    (set) => ({
      ...initialState,
      
      setAuth: (data: LoginResponse) => {
        localStorage.setItem('auth_token', data.token);
        set({
          token: data.token,
          passengerId: data.passengerId,
          email: data.email,
          name: data.name,
          isAuthenticated: true,
        });
      },

      clearAuth: () => {
        localStorage.removeItem('auth_token');
        set(initialState);
      },

      updateToken: (token: string) => {
        localStorage.setItem('auth_token', token);
        set({ token });
      },
    }),
    {
      name: 'auth_state',
      partialize: (state) => ({
        passengerId: state.passengerId,
        email: state.email,
        name: state.name,
        isAuthenticated: state.isAuthenticated,
      }),
    }
  )
);
