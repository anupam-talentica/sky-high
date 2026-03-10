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
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
} from '@mui/material';
import FlightIcon from '@mui/icons-material/Flight';
import { flightService } from '../services/flight.service';
import { checkinService } from '../services/checkin.service';
import type { Airline, Airport, Flight } from '../types/flight.types';
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
  const [airlineDialogOpen, setAirlineDialogOpen] = useState(false);
  const [airportDialogOpen, setAirportDialogOpen] = useState(false);
  const [selectedAirline, setSelectedAirline] = useState<Airline | null>(null);
  const [selectedAirport, setSelectedAirport] = useState<Airport | null>(null);
  const [dialogLoading, setDialogLoading] = useState(false);
  const [dialogError, setDialogError] = useState<string | null>(null);
  const [weatherLoading, setWeatherLoading] = useState(false);

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

  const handleAirlineClick = async (flight: Flight) => {
    const airlineCode = flight.flightNumber.slice(0, 2).toUpperCase();
    try {
      setSelectedAirline(null);
      setDialogLoading(true);
      setDialogError(null);
      setAirlineDialogOpen(true);
      const airline = await flightService.getAirlineByIata(airlineCode);
      setSelectedAirline(airline);
    } catch (err) {
      const apiError = err as ApiError;
      setDialogError(apiError.message);
    } finally {
      setDialogLoading(false);
    }
  };

  const handleAirportClick = async (iataCode: string) => {
    try {
      setSelectedAirport(null);
      setDialogLoading(true);
      setDialogError(null);
      setWeatherLoading(false);
      setAirportDialogOpen(true);
      const airport = await flightService.getAirportByIata(iataCode);
      setSelectedAirport(airport);
    } catch (err) {
      const apiError = err as ApiError;
      setDialogError(apiError.message);
    } finally {
      setDialogLoading(false);
    }
  };

  const handleCheckWeather = async () => {
    if (!selectedAirport) return;
    try {
      setWeatherLoading(true);
      const enriched = await flightService.getAirportWeatherByIata(selectedAirport.iataCode);
      setSelectedAirport(enriched);
    } catch (err) {
      const apiError = err as ApiError;
      setDialogError(apiError.message);
    } finally {
      setWeatherLoading(false);
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
                  <Typography
                    variant="h5"
                    component="h2"
                    fontWeight="bold"
                    sx={{ cursor: 'pointer', textDecoration: 'underline' }}
                    onClick={() => handleAirlineClick(flight)}
                  >
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
                    <Typography
                      variant="h6"
                      sx={{ cursor: 'pointer', textDecoration: 'underline' }}
                      onClick={() => handleAirportClick(flight.origin)}
                    >
                      {flight.origin}
                    </Typography>
                    <Typography variant="body2" color="text.secondary">
                      {formatDateTime(flight.departureTime)}
                    </Typography>
                  </Box>
                  <FlightIcon sx={{ color: 'primary.main', fontSize: 30 }} />
                  <Box sx={{ flex: 1, textAlign: 'right' }}>
                    <Typography
                      variant="h6"
                      sx={{ cursor: 'pointer', textDecoration: 'underline' }}
                      onClick={() => handleAirportClick(flight.destination)}
                    >
                      {flight.destination}
                    </Typography>
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

      <Dialog open={airlineDialogOpen} onClose={() => setAirlineDialogOpen(false)} fullWidth maxWidth="sm">
        <DialogTitle>Airline Details</DialogTitle>
        <DialogContent dividers>
          {dialogLoading && <Loading message="Loading airline details..." />}
          {dialogError && <ErrorMessage message={dialogError} />}
          {!dialogLoading && !dialogError && selectedAirline && (
            <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
              <Typography variant="h6">{selectedAirline.name}</Typography>
              <Typography variant="body2" color="text.secondary">
                IATA: {selectedAirline.iataCode} • ICAO: {selectedAirline.icaoCode}
              </Typography>
              <Typography variant="body2" color="text.secondary">
                Country: {selectedAirline.countryName} ({selectedAirline.countryIso2})
              </Typography>
              {selectedAirline.callsign && (
                <Typography variant="body2" color="text.secondary">
                  Callsign: {selectedAirline.callsign}
                </Typography>
              )}
              {selectedAirline.website && (
                <Typography variant="body2" color="primary.main">
                  Website: {selectedAirline.website}
                </Typography>
              )}
              {selectedAirline.phone && (
                <Typography variant="body2" color="text.secondary">
                  Phone: {selectedAirline.phone}
                </Typography>
              )}
            </Box>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setAirlineDialogOpen(false)}>Close</Button>
        </DialogActions>
      </Dialog>

      <Dialog open={airportDialogOpen} onClose={() => setAirportDialogOpen(false)} fullWidth maxWidth="sm">
        <DialogTitle>Airport Details</DialogTitle>
        <DialogContent dividers>
          {dialogLoading && <Loading message="Loading airport details..." />}
          {dialogError && <ErrorMessage message={dialogError} />}
          {!dialogLoading && !dialogError && selectedAirport && (
            <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
              <Typography variant="h6">{selectedAirport.name}</Typography>
              <Typography variant="body2" color="text.secondary">
                {selectedAirport.city}, {selectedAirport.countryName} ({selectedAirport.countryIso2})
              </Typography>
              <Typography variant="body2" color="text.secondary">
                IATA: {selectedAirport.iataCode} • ICAO: {selectedAirport.icaoCode}
              </Typography>
              <Typography variant="body2" color="text.secondary">
                Timezone: {selectedAirport.timezone}
              </Typography>
              {typeof selectedAirport.temperatureC === 'number' ? (
                <Typography variant="body2" color="text.secondary">
                  Weather: {selectedAirport.temperatureC.toFixed(1)}°C
                  {selectedAirport.weatherDescription ? `, ${selectedAirport.weatherDescription}` : ''}
                </Typography>
              ) : (
                <Box sx={{ mt: 1, display: 'flex', alignItems: 'center', gap: 1 }}>
                  <Button
                    variant="text"
                    size="small"
                    onClick={handleCheckWeather}
                    disabled={weatherLoading}
                    sx={{ textTransform: 'none', p: 0, minWidth: 0 }}
                  >
                    <Typography
                      variant="body2"
                      color="primary"
                      sx={{ textDecoration: 'underline' }}
                    >
                      Check weather now
                    </Typography>
                  </Button>
                  {weatherLoading && (
                    <Typography variant="body2" color="text.secondary">
                      Fetching weather...
                    </Typography>
                  )}
                </Box>
              )}
            </Box>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setAirportDialogOpen(false)}>Close</Button>
        </DialogActions>
      </Dialog>
    </Container>
  );
};
