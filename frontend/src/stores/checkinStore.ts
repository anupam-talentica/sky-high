import { create } from 'zustand';
import type { CheckInStatus } from '../types/checkin.types';
import type { Seat } from '../types/seat.types';

interface CheckInFlowState {
  checkInId: string | null;
  flightId: string | null;
  selectedSeat: Seat | null;
  baggageItems: Array<{
    baggageId?: number;
    weight: number;
    unit: string;
    type: 'carry_on' | 'checked';
    fee: number;
  }>;
  totalFee: number;
  currentStep: number;
  status: CheckInStatus | null;
  seatHoldTimer: number | null;
}

interface CheckInStore extends CheckInFlowState {
  setCheckInId: (checkInId: string) => void;
  setFlightId: (flightId: string) => void;
  setSelectedSeat: (seat: Seat | null) => void;
  addBaggageItem: (item: {
    baggageId?: number;
    weight: number;
    unit: string;
    type: 'carry_on' | 'checked';
    fee: number;
  }) => void;
  setBaggageItems: (items: Array<{
    baggageId?: number;
    weight: number;
    unit: string;
    type: 'carry_on' | 'checked';
    fee: number;
  }>) => void;
  removeBaggageItem: (index: number) => void;
  setTotalFee: (fee: number) => void;
  setCurrentStep: (step: number) => void;
  setStatus: (status: CheckInStatus) => void;
  setSeatHoldTimer: (seconds: number | null) => void;
  resetCheckIn: () => void;
  nextStep: () => void;
  previousStep: () => void;
}

const initialState: CheckInFlowState = {
  checkInId: null,
  flightId: null,
  selectedSeat: null,
  baggageItems: [],
  totalFee: 0,
  currentStep: 0,
  status: null,
  seatHoldTimer: null,
};

export const useCheckinStore = create<CheckInStore>((set) => ({
  ...initialState,

  setCheckInId: (checkInId: string) => set({ checkInId }),

  setFlightId: (flightId: string) => set({ flightId }),

  setSelectedSeat: (seat: Seat | null) => set({ selectedSeat: seat }),

  addBaggageItem: (item) =>
    set((state) => ({
      baggageItems: [...state.baggageItems, item],
      totalFee: state.totalFee + item.fee,
    })),

  setBaggageItems: (items) =>
    set({
      baggageItems: items,
      totalFee: items.reduce((sum, item) => sum + item.fee, 0),
    }),

  removeBaggageItem: (index) =>
    set((state) => {
      const removedItem = state.baggageItems[index];
      return {
        baggageItems: state.baggageItems.filter((_, i) => i !== index),
        totalFee: state.totalFee - removedItem.fee,
      };
    }),

  setTotalFee: (fee: number) => set({ totalFee: fee }),

  setCurrentStep: (step: number) => set({ currentStep: step }),

  setStatus: (status: CheckInStatus) => set({ status }),

  setSeatHoldTimer: (seconds: number | null) => set({ seatHoldTimer: seconds }),

  resetCheckIn: () => set(initialState),

  nextStep: () => set((state) => ({ currentStep: state.currentStep + 1 })),

  previousStep: () =>
    set((state) => ({
      currentStep: Math.max(0, state.currentStep - 1),
    })),
}));
