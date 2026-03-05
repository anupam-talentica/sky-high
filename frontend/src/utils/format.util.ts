export const formatCurrency = (amount: number): string => {
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
  }).format(amount);
};

export const formatWeight = (weight: number, unit: string): string => {
  return `${weight.toFixed(2)} ${unit}`;
};

export const formatSeatNumber = (seatNumber: string): string => {
  return seatNumber.toUpperCase();
};

export const formatFlightNumber = (flightNumber: string): string => {
  return flightNumber.toUpperCase();
};
