import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Box,
  Button,
  Container,
  Typography,
  Paper,
  Divider,
  Alert,
  List,
  ListItem,
  ListItemText,
} from '@mui/material';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import DownloadIcon from '@mui/icons-material/Download';
import PersonIcon from '@mui/icons-material/Person';
import AirlineSeatReclineNormalIcon from '@mui/icons-material/AirlineSeatReclineNormal';
import LuggageIcon from '@mui/icons-material/Luggage';
import { checkinService } from '../services/checkin.service';
import { useCheckinStore } from '../stores/checkinStore';
import { useAuthStore } from '../stores/authStore';
import { Loading } from '../components/Loading';
import { ErrorMessage } from '../components/ErrorMessage';
import type { ApiError } from '../types/api.types';
import type { CheckInDetailsResponse } from '../types/checkin.types';

export const ConfirmationPage: React.FC = () => {
  const navigate = useNavigate();
  const { checkInId, flightId, resetCheckIn, selectedSeat, baggageItems, totalFee } = useCheckinStore();
  const { passengerId } = useAuthStore();

  const [loading, setLoading] = useState(true);
  const [confirming, setConfirming] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [boardingPass, setBoardingPass] = useState<string | null>(null);
  const [checkInDetails, setCheckInDetails] = useState<CheckInDetailsResponse | null>(null);
  const [isConfirmed, setIsConfirmed] = useState(false);

  useEffect(() => {
    if (!checkInId) {
      navigate('/flights');
      return;
    }
    loadCheckInDetails();
  }, [checkInId]);

  const loadCheckInDetails = async () => {
    if (!checkInId) return;

    try {
      setLoading(true);
      const details = await checkinService.getCheckInDetails(checkInId);
      setCheckInDetails(details);
      
      if (details.status === 'COMPLETED' && details.boardingPass) {
        setBoardingPass(details.boardingPass);
        setIsConfirmed(true);
      }
    } catch (err) {
      const apiError = err as ApiError;
      setError(apiError.message);
    } finally {
      setLoading(false);
    }
  };

  const showCheckInSuccessNotification = (flightNumber: string) => {
    if (typeof window === 'undefined' || !('Notification' in window)) return;
    if (Notification.permission === 'granted') {
      new Notification('Check-in complete', {
        body: `Check-in for flight #${flightNumber} is successfully completed.`,
      });
    } else if (Notification.permission !== 'denied') {
      Notification.requestPermission().then((permission) => {
        if (permission === 'granted') {
          new Notification('Check-in complete', {
            body: `Check-in for flight #${flightNumber} is successfully completed.`,
          });
        }
      });
    }
  };

  const confirmCheckIn = async () => {
    if (!checkInId) return;

    try {
      setConfirming(true);
      setError(null);
      await checkinService.confirmCheckIn(checkInId);
      const flightNumber = flightId || checkInDetails?.flightId || '—';
      showCheckInSuccessNotification(flightNumber);
      resetCheckIn();
      navigate('/flights');
    } catch (err) {
      const apiError = err as ApiError;
      setError(apiError.message);
    } finally {
      setConfirming(false);
    }
  };

  const handleDownloadBoardingPass = () => {
    if (!boardingPass) return;

    const blob = new Blob([boardingPass], { type: 'text/plain' });
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `boarding-pass-${checkInId}.txt`;
    document.body.appendChild(a);
    a.click();
    window.URL.revokeObjectURL(url);
    document.body.removeChild(a);
  };

  const handleNewCheckIn = () => {
    resetCheckIn();
    navigate('/flights');
  };

  if (loading) {
    return <Loading message="Loading check-in details..." fullScreen />;
  }

  if (error && !isConfirmed) {
    return (
      <Container maxWidth="md" sx={{ py: 4 }}>
        <ErrorMessage message={error} />
        <Box sx={{ mt: 3, textAlign: 'center' }}>
          <Button variant="contained" onClick={() => navigate('/flights')}>
            Return to Flights
          </Button>
        </Box>
      </Container>
    );
  }

  // Show boarding pass after confirmation
  if (isConfirmed && boardingPass) {
    const displayBaggageDetails = checkInDetails?.baggageDetails || baggageItems;
    const displayTotalFee = checkInDetails?.totalFee || totalFee;

    return (
      <Container maxWidth="md" sx={{ py: 4 }}>
        <Box sx={{ textAlign: 'center', mb: 4 }}>
          <CheckCircleIcon sx={{ fontSize: 80, color: 'success.main', mb: 2 }} />
          <Typography variant="h3" component="h1" gutterBottom fontWeight="bold">
            Check-In Successful!
          </Typography>
          <Typography variant="h6" color="text.secondary">
            Your boarding pass is ready
          </Typography>
        </Box>

        <Paper elevation={3} sx={{ p: 4, mb: 3 }}>
          <Typography variant="h5" gutterBottom fontWeight="bold" textAlign="center">
            Boarding Pass
          </Typography>
          <Divider sx={{ my: 2 }} />

          <Box sx={{ my: 3 }}>
            <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 2 }}>
              <Typography variant="body1" color="text.secondary">
                Check-In ID:
              </Typography>
              <Typography variant="body1" fontWeight="bold">
                {checkInId}
              </Typography>
            </Box>

            <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 2 }}>
              <Typography variant="body1" color="text.secondary">
                Seat Number:
              </Typography>
              <Typography variant="h5" fontWeight="bold" color="primary">
                {checkInDetails?.seatNumber || selectedSeat?.seatNumber}
              </Typography>
            </Box>
          </Box>

          <Divider sx={{ my: 2 }} />

          <Box
            sx={{
              backgroundColor: 'grey.100',
              p: 2,
              borderRadius: 1,
              fontFamily: 'monospace',
              fontSize: '0.9rem',
              whiteSpace: 'pre-wrap',
              wordBreak: 'break-all',
              maxHeight: 200,
              overflow: 'auto',
            }}
          >
            {boardingPass}
          </Box>
        </Paper>

        {displayBaggageDetails && displayBaggageDetails.length > 0 && (
          <Paper elevation={2} sx={{ p: 3, mb: 3 }}>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 2 }}>
              <LuggageIcon color="primary" />
              <Typography variant="h6">Baggage Summary</Typography>
            </Box>
            <Divider sx={{ mb: 2 }} />
            <List dense>
              {displayBaggageDetails.map((item: any, index: number) => {
                const isApiBaggage = 'baggageType' in item;
                const typeLabel = isApiBaggage
                  ? item.baggageType === 'CARRY_ON'
                    ? 'Carry-On'
                    : 'Checked'
                  : item.type === 'carry_on'
                  ? 'Carry-On'
                  : 'Checked';
                const weightKg = isApiBaggage ? item.weightKg : item.weight;
                const fee = isApiBaggage ? item.excessFee : item.fee;

                return (
                  <ListItem key={item.baggageId || index}>
                    <ListItemText
                      primary={`${typeLabel} - ${weightKg} KG`}
                      secondary={`Excess Fee: $${(fee || 0).toFixed(2)}`}
                    />
                  </ListItem>
                );
              })}
            </List>
            <Divider sx={{ my: 2 }} />
            <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
              <Typography variant="h6">Total Baggage Fee:</Typography>
              <Typography variant="h6" color="primary">
                ${(displayTotalFee || 0).toFixed(2)}
              </Typography>
            </Box>
          </Paper>
        )}

        <Alert severity="info" sx={{ mb: 3 }}>
          Please arrive at the gate at least 30 minutes before departure. Have your boarding pass
          ready for scanning.
        </Alert>

        <Box sx={{ display: 'flex', gap: 2, justifyContent: 'center' }}>
          <Button
            variant="outlined"
            startIcon={<DownloadIcon />}
            onClick={handleDownloadBoardingPass}
            disabled={!boardingPass}
          >
            Download Boarding Pass
          </Button>
          <Button variant="contained" onClick={handleNewCheckIn}>
            New Check-In
          </Button>
        </Box>
      </Container>
    );
  }

  // Show review page before confirmation
  return (
    <Container maxWidth="md" sx={{ py: 4 }}>
      <Box sx={{ mb: 4 }}>
        <Typography variant="h4" component="h1" gutterBottom>
          Review & Confirm
        </Typography>
        <Typography variant="body1" color="text.secondary">
          Please review your check-in details before confirming
        </Typography>
      </Box>

      {error && <ErrorMessage message={error} />}

      {/* Passenger Details */}
      <Paper elevation={2} sx={{ p: 3, mb: 3 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 2 }}>
          <PersonIcon color="primary" />
          <Typography variant="h6">Passenger Details</Typography>
        </Box>
        <Divider sx={{ mb: 2 }} />
        <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 1 }}>
          <Typography color="text.secondary">Passenger ID:</Typography>
          <Typography fontWeight="bold">{passengerId || checkInDetails?.passengerId}</Typography>
        </Box>
        <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 1 }}>
          <Typography color="text.secondary">Flight:</Typography>
          <Typography fontWeight="bold">{checkInDetails?.flightId}</Typography>
        </Box>
        <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
          <Typography color="text.secondary">Check-In ID:</Typography>
          <Typography fontWeight="bold">{checkInId}</Typography>
        </Box>
      </Paper>

      {/* Seat Details */}
      <Paper elevation={2} sx={{ p: 3, mb: 3 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 2 }}>
          <AirlineSeatReclineNormalIcon color="primary" />
          <Typography variant="h6">Seat Assignment</Typography>
        </Box>
        <Divider sx={{ mb: 2 }} />
        <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 1 }}>
          <Typography color="text.secondary">Seat Number:</Typography>
          <Typography variant="h5" fontWeight="bold" color="primary">
            {checkInDetails?.seatNumber || selectedSeat?.seatNumber}
          </Typography>
        </Box>
        {selectedSeat?.seatType && (
          <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
            <Typography color="text.secondary">Seat Type:</Typography>
            <Typography>{selectedSeat.seatType}</Typography>
          </Box>
        )}
      </Paper>

      {/* Baggage Summary */}
      <Paper elevation={2} sx={{ p: 3, mb: 3 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 2 }}>
          <LuggageIcon color="primary" />
          <Typography variant="h6">Baggage Summary</Typography>
        </Box>
        <Divider sx={{ mb: 2 }} />
        {baggageItems.length > 0 ? (
          <>
            <List dense>
              {baggageItems.map((item, index) => (
                <ListItem key={index}>
                  <ListItemText
                    primary={`${item.type === 'carry_on' ? 'Carry-On' : 'Checked'} - ${item.weight} ${item.unit}`}
                    secondary={`Fee: $${(item.fee || 0).toFixed(2)}`}
                  />
                </ListItem>
              ))}
            </List>
            <Divider sx={{ my: 2 }} />
            <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
              <Typography variant="h6">Total Baggage Fee:</Typography>
              <Typography variant="h6" color="primary">
                ${(totalFee || 0).toFixed(2)}
              </Typography>
            </Box>
          </>
        ) : (
          <Typography color="text.secondary">No baggage added</Typography>
        )}
      </Paper>

      {totalFee === 0 && (
        <Alert severity="success" sx={{ mb: 3 }}>
          Great! Your baggage is within the free allowance (20 KG). No payment required.
        </Alert>
      )}

      <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
        <Button variant="outlined" onClick={() => navigate('/checkin/baggage')}>
          Back
        </Button>
        <Button
          variant="contained"
          onClick={confirmCheckIn}
          disabled={confirming}
          size="large"
        >
          {confirming ? 'Confirming...' : 'Confirm Check-In'}
        </Button>
      </Box>
    </Container>
  );
};
