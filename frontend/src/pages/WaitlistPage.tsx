import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Box,
  Button,
  Container,
  Typography,
  Paper,
  List,
  ListItem,
  ListItemText,
  Chip,
  IconButton,
  Alert,
} from '@mui/material';
import DeleteIcon from '@mui/icons-material/Delete';
import RefreshIcon from '@mui/icons-material/Refresh';
import { waitlistService } from '../services/waitlist.service';
import type { Waitlist } from '../types/waitlist.types';
import { WaitlistStatus } from '../types/waitlist.types';
import { useAuthStore } from '../stores/authStore';
import { Loading } from '../components/Loading';
import { ErrorMessage } from '../components/ErrorMessage';
import type { ApiError } from '../types/api.types';

export const WaitlistPage: React.FC = () => {
  const navigate = useNavigate();
  const passengerId = useAuthStore((state) => state.passengerId);

  const [waitlists, setWaitlists] = useState<Waitlist[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    loadWaitlists();
    const interval = setInterval(loadWaitlists, 10000);
    return () => clearInterval(interval);
  }, [passengerId]);

  const loadWaitlists = async () => {
    if (!passengerId) return;

    try {
      const response = await waitlistService.getPassengerWaitlists(passengerId);
      setWaitlists(response);
      setError(null);
    } catch (err) {
      const apiError = err as ApiError;
      setError(apiError.message);
    } finally {
      setLoading(false);
    }
  };

  const handleLeaveWaitlist = async (waitlistId: number) => {
    try {
      await waitlistService.leaveWaitlist(waitlistId);
      await loadWaitlists();
    } catch (err) {
      const apiError = err as ApiError;
      setError(apiError.message);
    }
  };

  const getStatusColor = (status: WaitlistStatus) => {
    switch (status) {
      case WaitlistStatus.WAITING:
        return 'warning';
      case WaitlistStatus.NOTIFIED:
        return 'info';
      case WaitlistStatus.ASSIGNED:
        return 'success';
      case WaitlistStatus.EXPIRED:
        return 'error';
      default:
        return 'default';
    }
  };

  const formatDate = (dateString: string) => {
    return new Date(dateString).toLocaleString('en-US', {
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  };

  if (loading && waitlists.length === 0) {
    return <Loading message="Loading waitlist..." fullScreen />;
  }

  return (
    <Container maxWidth="md" sx={{ py: 4 }}>
      <Box sx={{ mb: 4, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <Box>
          <Typography variant="h4" component="h1" gutterBottom>
            My Waitlist
          </Typography>
          <Typography variant="body1" color="text.secondary">
            Track your waitlist positions
          </Typography>
        </Box>
        <IconButton onClick={loadWaitlists} disabled={loading}>
          <RefreshIcon />
        </IconButton>
      </Box>

      {error && <ErrorMessage message={error} />}

      {waitlists.length === 0 ? (
        <Paper elevation={2} sx={{ p: 4, textAlign: 'center' }}>
          <Typography variant="h6" color="text.secondary" gutterBottom>
            No Active Waitlists
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
            You are not currently on any waitlists
          </Typography>
          <Button variant="contained" onClick={() => navigate('/flights')}>
            Browse Flights
          </Button>
        </Paper>
      ) : (
        <List sx={{ width: '100%' }}>
          {waitlists.map((waitlist) => (
            <Paper key={waitlist.waitlistId} elevation={2} sx={{ mb: 2 }}>
              <ListItem
                secondaryAction={
                  waitlist.status === WaitlistStatus.WAITING && (
                    <IconButton edge="end" onClick={() => handleLeaveWaitlist(waitlist.waitlistId)}>
                      <DeleteIcon />
                    </IconButton>
                  )
                }
              >
                <ListItemText
                  primary={
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 1 }}>
                      <Typography variant="h6">
                        Flight {waitlist.flightId} - Seat {waitlist.seatNumber}
                      </Typography>
                      <Chip
                        label={waitlist.status}
                        color={getStatusColor(waitlist.status)}
                        size="small"
                      />
                    </Box>
                  }
                  secondary={
                    <Box>
                      <Typography variant="body2" color="text.secondary">
                        Position: #{waitlist.position}
                      </Typography>
                      <Typography variant="body2" color="text.secondary">
                        Joined: {formatDate(waitlist.joinedAt)}
                      </Typography>
                      {waitlist.notifiedAt && (
                        <Typography variant="body2" color="text.secondary">
                          Notified: {formatDate(waitlist.notifiedAt)}
                        </Typography>
                      )}
                      {waitlist.assignedAt && (
                        <Typography variant="body2" color="success.main">
                          Assigned: {formatDate(waitlist.assignedAt)}
                        </Typography>
                      )}
                    </Box>
                  }
                />
              </ListItem>

              {waitlist.status === WaitlistStatus.NOTIFIED && (
                <Alert severity="success" sx={{ m: 2, mt: 0 }}>
                  <Typography variant="body2">
                    Good news! A seat is available. Please complete your check-in soon.
                  </Typography>
                  <Button
                    variant="outlined"
                    size="small"
                    sx={{ mt: 1 }}
                    onClick={() => navigate('/flights')}
                  >
                    Complete Check-In
                  </Button>
                </Alert>
              )}

              {waitlist.status === WaitlistStatus.ASSIGNED && (
                <Alert severity="info" sx={{ m: 2, mt: 0 }}>
                  <Typography variant="body2">
                    Your seat has been assigned. Check your email for details.
                  </Typography>
                </Alert>
              )}
            </Paper>
          ))}
        </List>
      )}

      <Box sx={{ mt: 3, textAlign: 'center' }}>
        <Button variant="outlined" onClick={() => navigate('/flights')}>
          Back to Flights
        </Button>
      </Box>
    </Container>
  );
};
