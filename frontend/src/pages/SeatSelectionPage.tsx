import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Box,
  Button,
  Container,
  Typography,
  Paper,
  Alert,
  LinearProgress,
} from '@mui/material';
import { seatService } from '../services/seat.service';
import { checkinService } from '../services/checkin.service';
import { waitlistService } from '../services/waitlist.service';
import type { Seat } from '../types/seat.types';
import { useAuthStore } from '../stores/authStore';
import { useCheckinStore } from '../stores/checkinStore';
import { SeatMap } from '../components/SeatMap';
import { Loading } from '../components/Loading';
import { ErrorMessage } from '../components/ErrorMessage';
import type { ApiError } from '../types/api.types';

export const SeatSelectionPage: React.FC = () => {
  const navigate = useNavigate();
  const passengerId = useAuthStore((state) => state.passengerId);
  const { checkInId, flightId, selectedSeat, setSelectedSeat, setSeatHoldTimer } =
    useCheckinStore();

  const [seats, setSeats] = useState<Seat[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [reserving, setReserving] = useState(false);
  const [timer, setTimer] = useState<number | null>(null);
  const [showWaitlistOption, setShowWaitlistOption] = useState(false);

  useEffect(() => {
    if (!checkInId || !flightId) {
      navigate('/flights');
      return;
    }
    checkExistingCheckIn();
  }, [checkInId, flightId]);

  const checkExistingCheckIn = async () => {
    if (!checkInId || !flightId) return;

    try {
      setLoading(true);
      const checkInDetails = await checkinService.getCheckInDetails(checkInId);
      
      // Load seats first
      const seatMapResponse = await seatService.getSeatMap(flightId);
      setSeats(seatMapResponse.seats);
      
      // If seat is already reserved in backend, populate the store
      if (checkInDetails.seatNumber) {
        const selectedSeatData = seatMapResponse.seats.find(
          (s) => s.seatNumber === checkInDetails.seatNumber
        );
        
        if (selectedSeatData) {
          setSelectedSeat(selectedSeatData);
          // Check if seat is actually held - if yes, start timer
          if (selectedSeatData.state === 'HELD' && selectedSeatData.heldBy === passengerId) {
            // Seat is reserved, show timer (though we don't know exact time remaining)
            setTimer(60); // Show some time remaining
            setSeatHoldTimer(60);
          }
        }
      }
      
      setLoading(false);
    } catch (err) {
      const apiError = err as ApiError;
      setError(apiError.message);
      setLoading(false);
    }
  };

  useEffect(() => {
    if (timer !== null && timer > 0) {
      const interval = setInterval(() => {
        setTimer((prev) => (prev !== null && prev > 0 ? prev - 1 : null));
      }, 1000);
      return () => clearInterval(interval);
    } else if (timer === 0) {
      handleTimerExpired();
    }
  }, [timer]);

  const loadSeats = async () => {
    if (!flightId) return;

    try {
      setLoading(true);
      const response = await seatService.getSeatMap(flightId);
      setSeats(response.seats);
    } catch (err) {
      const apiError = err as ApiError;
      setError(apiError.message);
    } finally {
      setLoading(false);
    }
  };

  const handleSeatSelect = (seat: Seat) => {
    // Just select the seat in UI, don't make backend call yet
    setSelectedSeat(seat);
    setError(null);

    // If the seat is held by another passenger, immediately offer waitlist option
    if (seat.state === 'HELD' && seat.heldBy && seat.heldBy !== passengerId) {
      setShowWaitlistOption(true);
    } else {
      setShowWaitlistOption(false);
    }
  };

  const handleJoinWaitlist = async () => {
    if (!passengerId || !flightId || !selectedSeat) return;

    try {
      setLoading(true);
      await waitlistService.joinWaitlist({
        passengerId,
        flightId,
        seatNumber: selectedSeat.seatNumber,
      });
      navigate('/checkin/waitlist');
    } catch (err) {
      const apiError = err as ApiError;
      setError(apiError.message);
    } finally {
      setLoading(false);
    }
  };

  const handleTimerExpired = async () => {
    if (selectedSeat && flightId) {
      try {
        await seatService.releaseSeat(flightId, selectedSeat.seatNumber);
        setSelectedSeat(null);
        setTimer(null);
        setSeatHoldTimer(null);
        await loadSeats();
        setError('Seat hold expired. Please select a seat again.');
      } catch (err) {
        console.error('Error releasing seat:', err);
      }
    }
  };

  const handleContinue = async () => {
    if (!passengerId || !flightId || !checkInId || !selectedSeat) return;

    // If the currently selected seat is held by another passenger, block continue and
    // guide the user to join the waitlist instead.
    if (selectedSeat.state === 'HELD' && selectedSeat.heldBy && selectedSeat.heldBy !== passengerId) {
      setError('This seat is currently held by another passenger.');
      setShowWaitlistOption(true);
      return;
    }

    try {
      setReserving(true);
      setError(null);

      // Check if seat is already reserved (user came back from baggage page)
      const isAlreadyReserved = selectedSeat.state === 'HELD' && selectedSeat.heldBy === passengerId;
      
      if (!isAlreadyReserved) {
        // Reserve the seat and link it to check-in
        await checkinService.selectSeat(checkInId, selectedSeat.seatId, passengerId);
        
        // Start the 120 second timer
        setTimer(120);
        setSeatHoldTimer(120);
      }

      // Navigate to baggage page
      navigate('/checkin/baggage');
    } catch (err) {
      const apiError = err as ApiError;
      setError(apiError.message);

      if (apiError.status === 409) {
        setShowWaitlistOption(true);
      }
    } finally {
      setReserving(false);
    }
  };

  const formatTimer = (seconds: number): string => {
    const mins = Math.floor(seconds / 60);
    const secs = seconds % 60;
    return `${mins}:${secs.toString().padStart(2, '0')}`;
  };

  if (loading && seats.length === 0) {
    return <Loading message="Loading seat map..." fullScreen />;
  }

  const isAlreadyReserved = selectedSeat?.state === 'HELD' && selectedSeat?.heldBy === passengerId;

  return (
    <Container maxWidth="lg" sx={{ py: 4 }}>
      <Box sx={{ mb: 4 }}>
        <Typography variant="h4" component="h1" gutterBottom>
          {isAlreadyReserved ? 'Your Reserved Seat' : 'Select Your Seat'}
        </Typography>
        <Typography variant="body1" color="text.secondary">
          {isAlreadyReserved 
            ? `You have selected seat ${selectedSeat?.seatNumber}. Continue to complete your check-in.`
            : 'Choose your preferred seat from the available options'}
        </Typography>
      </Box>

      {selectedSeat && (
        <Paper elevation={2} sx={{ p: 2, mb: 3, backgroundColor: isAlreadyReserved ? 'success.light' : timer !== null ? 'info.light' : 'grey.100' }}>
          <Typography variant="h6" gutterBottom>
            Seat {selectedSeat.seatNumber} {isAlreadyReserved ? 'Reserved' : timer !== null ? 'Reserved' : 'Selected'}
          </Typography>
          {timer !== null && !isAlreadyReserved && (
            <>
              <Typography variant="body2" gutterBottom>
                Time remaining: {formatTimer(timer)}
              </Typography>
              <LinearProgress
                variant="determinate"
                value={(timer / 120) * 100}
                sx={{ mt: 1 }}
              />
            </>
          )}
          {isAlreadyReserved && (
            <Typography variant="body2" color="text.secondary">
              Your seat is reserved. Continue to add baggage and complete payment.
            </Typography>
          )}
          {timer === null && !isAlreadyReserved && (
            <Typography variant="body2" color="text.secondary">
              Click "Continue to Baggage" to reserve this seat
            </Typography>
          )}
        </Paper>
      )}

      {error && (
        <Box sx={{ mb: 3 }}>
          <ErrorMessage message={error} />
        </Box>
      )}

      {showWaitlistOption && selectedSeat && (
        <Box sx={{ mb: 3 }}>
          <Alert severity="info">
            <Typography variant="body2" gutterBottom>
              Seat {selectedSeat.seatNumber} is currently held by another passenger. You can join the
              waitlist to be notified if it becomes available.
            </Typography>
            <Button variant="outlined" size="small" onClick={handleJoinWaitlist} sx={{ mt: 1 }}>
              Join Waitlist
            </Button>
          </Alert>
        </Box>
      )}

      <SeatMap
        seats={seats}
        selectedSeatNumber={selectedSeat?.seatNumber || null}
        onSeatSelect={handleSeatSelect}
        disabled={reserving}
        allowSelectHeldSeats
      />

      <Box sx={{ mt: 4, display: 'flex', justifyContent: 'space-between' }}>
        <Button variant="outlined" onClick={() => navigate('/flights')}>
          Back to Flights
        </Button>
        <Button
          variant="contained"
          onClick={handleContinue}
          disabled={!selectedSeat || reserving}
        >
          Continue to Baggage
        </Button>
      </Box>
    </Container>
  );
};
