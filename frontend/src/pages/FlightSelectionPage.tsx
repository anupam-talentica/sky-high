import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Box,
  Button,
  Card,
  CardContent,
  Container,
  Typography,
  Chip,
} from '@mui/material';
import FlightIcon from '@mui/icons-material/Flight';
import { flightService } from '../services/flight.service';
import { checkinService } from '../services/checkin.service';
import type { Flight } from '../types/flight.types';
import { useAuthStore } from '../stores/authStore';
import { useCheckinStore } from '../stores/checkinStore';
import { Loading } from '../components/Loading';
import { ErrorMessage } from '../components/ErrorMessage';
import type { ApiError } from '../types/api.types';
import { CheckInStatus, type CheckIn } from '../types/checkin.types';

export const FlightSelectionPage: React.FC = () => {
  const navigate = useNavigate();
  const passengerId = useAuthStore((state) => state.passengerId);
  const { setCheckInId, setFlightId, resetCheckIn } = useCheckinStore();

  const [flights, setFlights] = useState<Flight[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [completedCheckInsByFlight, setCompletedCheckInsByFlight] = useState<Record<string, CheckIn>>({});

  useEffect(() => {
    resetCheckIn();

    const initialize = async () => {
      await loadFlights();
      if (passengerId) {
        await loadPassengerCheckIns(passengerId);
      }
    };

    void initialize();
  }, [passengerId]);

  const loadFlights = async () => {
    try {
      setLoading(true);
      const response = await flightService.getAllFlights();
      // Backend already filters flights by the logged-in passenger's reservations
      setFlights(response.flights);
    } catch (err) {
      const apiError = err as ApiError;
      setError(apiError.message);
    } finally {
      setLoading(false);
    }
  };

  const loadPassengerCheckIns = async (currentPassengerId: string) => {
    try {
      const checkIns = await checkinService.getPassengerCheckIns(currentPassengerId);
      const completedByFlight: Record<string, CheckIn> = {};

      checkIns.forEach((checkIn) => {
        if (checkIn.status === CheckInStatus.COMPLETED) {
          completedByFlight[checkIn.flightId] = checkIn;
        }
      });

      setCompletedCheckInsByFlight(completedByFlight);
    } catch {
      // Silently ignore check-in loading errors so flight loading isn't blocked
    }
  };

  const handleFlightSelect = async (flight: Flight) => {
    if (!passengerId) return;

    try {
      setLoading(true);
      const response = await checkinService.initiateCheckIn({
        passengerId,
        flightId: flight.flightId,
      });
      setCheckInId(response.checkInId);
      setFlightId(flight.flightId);
      navigate('/checkin/seat-selection');
    } catch (err) {
      const apiError = err as ApiError;
      setError(apiError.message);
    } finally {
      setLoading(false);
    }
  };

  const handleViewBoardingPass = (flight: Flight, checkIn: CheckIn) => {
    setCheckInId(checkIn.checkInId);
    setFlightId(flight.flightId);
    navigate('/checkin/confirmation');
  };

  const formatDateTime = (dateTime: string) => {
    return new Date(dateTime).toLocaleString('en-US', {
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  };

  if (loading && flights.length === 0) {
    return <Loading message="Loading flights..." fullScreen />;
  }

  return (
    <Container maxWidth="lg" sx={{ py: 4 }}>
      <Box sx={{ mb: 4 }}>
        <Typography variant="h4" component="h1" gutterBottom>
          Select Your Flight
        </Typography>
        <Typography variant="body1" color="text.secondary">
          Choose a flight to begin check-in
        </Typography>
      </Box>

      {error && <ErrorMessage message={error} onRetry={loadFlights} />}

      <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: 'repeat(2, 1fr)' }, gap: 3 }}>
        {flights.map((flight) => {
          const completedCheckIn = completedCheckInsByFlight[flight.flightId];
          const isCheckInCompleted = completedCheckIn?.status === CheckInStatus.COMPLETED;

          return (
            <Box key={flight.flightId}>
              <Card
              elevation={2}
              sx={{
                height: '100%',
                display: 'flex',
                flexDirection: 'column',
                transition: 'transform 0.2s',
                '&:hover': {
                  transform: 'translateY(-4px)',
                  boxShadow: 4,
                },
              }}
              >
                <CardContent sx={{ flexGrow: 1 }}>
                <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 2 }}>
                  <Typography variant="h5" component="h2" fontWeight="bold">
                    {flight.flightNumber}
                  </Typography>
                  <Chip
                    label={isCheckInCompleted ? 'Check-In Completed' : flight.status}
                    color={isCheckInCompleted ? 'primary' : flight.status === 'SCHEDULED' ? 'success' : 'default'}
                    size="small"
                  />
                </Box>

                <Box sx={{ display: 'flex', alignItems: 'center', mb: 2, gap: 2 }}>
                  <Box sx={{ flex: 1 }}>
                    <Typography variant="h6">{flight.origin}</Typography>
                    <Typography variant="body2" color="text.secondary">
                      {formatDateTime(flight.departureTime)}
                    </Typography>
                  </Box>
                  <FlightIcon sx={{ color: 'primary.main', fontSize: 30 }} />
                  <Box sx={{ flex: 1, textAlign: 'right' }}>
                    <Typography variant="h6">{flight.destination}</Typography>
                    <Typography variant="body2" color="text.secondary">
                      {formatDateTime(flight.arrivalTime)}
                    </Typography>
                  </Box>
                </Box>

                <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 2 }}>
                  <Typography variant="body2" color="text.secondary">
                    Aircraft: {flight.aircraftType}
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    Available Seats: {flight.availableSeats}/{flight.totalSeats}
                  </Typography>
                </Box>

                <Button
                  variant="contained"
                  fullWidth
                  onClick={() =>
                    isCheckInCompleted && completedCheckIn
                      ? handleViewBoardingPass(flight, completedCheckIn)
                      : handleFlightSelect(flight)
                  }
                  disabled={loading || flight.availableSeats === 0}
                >
                  {flight.availableSeats === 0
                    ? 'Fully Booked'
                    : isCheckInCompleted
                    ? 'View Boarding Pass'
                    : 'Check In'}
                </Button>
              </CardContent>
            </Card>
          </Box>
        );
        })}
      </Box>

      {flights.length === 0 && !loading && (
        <Box sx={{ textAlign: 'center', py: 8 }}>
          <Typography variant="h6" color="text.secondary">
            No flights available at the moment
          </Typography>
        </Box>
      )}
    </Container>
  );
};
