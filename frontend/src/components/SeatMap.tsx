import React from 'react';
import {
  Box,
  Paper,
  Typography,
  Tooltip,
  useTheme,
  useMediaQuery,
} from '@mui/material';
import EventSeatIcon from '@mui/icons-material/EventSeat';
import type { Seat } from '../types/seat.types';
import { SeatState } from '../types/seat.types';

interface SeatMapProps {
  seats: Seat[];
  selectedSeatNumber: string | null;
  onSeatSelect: (seat: Seat) => void;
  disabled?: boolean;
  /**
   * When true, allows selecting seats that are in HELD state.
   * The parent component is responsible for deciding what to do with a held seat selection.
   */
  allowSelectHeldSeats?: boolean;
}

export const SeatMap: React.FC<SeatMapProps> = ({
  seats,
  selectedSeatNumber,
  onSeatSelect,
  disabled = false,
  allowSelectHeldSeats = false,
}) => {
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down('sm'));

  const getSeatColor = (seat: Seat): string => {
    // Always show HELD seats in the held color so users can clearly see
    // that the seat is not freely available, even when they click it.
    if (seat.state === SeatState.HELD) {
      return theme.palette.warning.light;
    }

    if (seat.seatNumber === selectedSeatNumber) {
      return theme.palette.info.main;
    }

    switch (seat.state) {
      case SeatState.AVAILABLE:
        return theme.palette.success.light;
      case SeatState.CONFIRMED:
      case SeatState.CANCELLED:
        return theme.palette.grey[400];
      default:
        return theme.palette.grey[300];
    }
  };

  const isSeatSelectable = (seat: Seat): boolean => {
    if (disabled) {
      return false;
    }

    if (seat.state === SeatState.AVAILABLE) {
      return true;
    }

    if (allowSelectHeldSeats && seat.state === SeatState.HELD) {
      return true;
    }

    return false;
  };

  const organizeSeats = () => {
    const rows: Record<string, Seat[]> = {};
    seats.forEach((seat) => {
      const row = seat.seatNumber.replace(/[A-Z]/g, '');
      if (!rows[row]) {
        rows[row] = [];
      }
      rows[row].push(seat);
    });

    Object.keys(rows).forEach((row) => {
      rows[row].sort((a, b) => a.seatNumber.localeCompare(b.seatNumber));
    });

    return rows;
  };

  const seatRows = organizeSeats();
  const rowNumbers = Object.keys(seatRows).sort((a, b) => parseInt(a) - parseInt(b));

  return (
    <Box sx={{ width: '100%' }}>
      <Box sx={{ mb: 3, display: 'flex', gap: 2, flexWrap: 'wrap', justifyContent: 'center' }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
          <Box
            sx={{
              width: 20,
              height: 20,
              backgroundColor: theme.palette.success.light,
              borderRadius: 1,
            }}
          />
          <Typography variant="body2">Available</Typography>
        </Box>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
          <Box
            sx={{
              width: 20,
              height: 20,
              backgroundColor: theme.palette.warning.light,
              borderRadius: 1,
            }}
          />
          <Typography variant="body2">Held</Typography>
        </Box>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
          <Box
            sx={{
              width: 20,
              height: 20,
              backgroundColor: theme.palette.grey[400],
              borderRadius: 1,
            }}
          />
          <Typography variant="body2">Unavailable</Typography>
        </Box>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
          <Box
            sx={{
              width: 20,
              height: 20,
              backgroundColor: theme.palette.info.main,
              borderRadius: 1,
            }}
          />
          <Typography variant="body2">Selected</Typography>
        </Box>
      </Box>

      <Paper
        elevation={2}
        sx={{
          p: isMobile ? 2 : 3,
          backgroundColor: 'grey.50',
          overflowX: 'auto',
        }}
      >
        <Typography
          variant="h6"
          align="center"
          sx={{ mb: 2, color: 'text.secondary', fontWeight: 'bold' }}
        >
          Front of Aircraft
        </Typography>

        <Box sx={{ minWidth: isMobile ? '300px' : 'auto' }}>
          {rowNumbers.map((rowNumber) => (
            <Box
              key={rowNumber}
              sx={{
                display: 'flex',
                justifyContent: 'center',
                alignItems: 'center',
                mb: 1,
                gap: 1,
              }}
            >
              <Typography
                variant="body2"
                sx={{
                  width: 30,
                  textAlign: 'right',
                  color: 'text.secondary',
                  fontWeight: 'bold',
                }}
              >
                {rowNumber}
              </Typography>

              <Box sx={{ display: 'flex', gap: 0.5 }}>
                {seatRows[rowNumber].map((seat, index) => {
                  const isAisle = index === 2;
                  return (
                    <React.Fragment key={seat.seatNumber}>
                      {isAisle && <Box sx={{ width: 20 }} />}
                      <Tooltip
                        title={
                          <Box>
                            <Typography variant="body2">
                              Seat: {seat.seatNumber}
                            </Typography>
                            <Typography variant="body2">
                              Type: {seat.seatType}
                            </Typography>
                            {seat.price && (
                              <Typography variant="body2">
                                Price: ${seat.price}
                              </Typography>
                            )}
                            <Typography variant="body2">
                              Status: {seat.state}
                            </Typography>
                            {seat.amenities && seat.amenities.length > 0 && (
                              <Typography variant="body2">
                                Amenities: {seat.amenities.join(', ')}
                              </Typography>
                            )}
                          </Box>
                        }
                      >
                        <Paper
                          elevation={seat.seatNumber === selectedSeatNumber ? 4 : 1}
                          sx={{
                            width: isMobile ? 40 : 50,
                            height: isMobile ? 40 : 50,
                            display: 'flex',
                            flexDirection: 'column',
                            alignItems: 'center',
                            justifyContent: 'center',
                            cursor: isSeatSelectable(seat) ? 'pointer' : 'not-allowed',
                            backgroundColor: getSeatColor(seat),
                            transition: 'all 0.2s',
                            '&:hover': isSeatSelectable(seat)
                              ? {
                                  transform: 'scale(1.1)',
                                  elevation: 4,
                                }
                              : {},
                          }}
                          onClick={() => isSeatSelectable(seat) && onSeatSelect(seat)}
                        >
                          <EventSeatIcon
                            sx={{
                              fontSize: isMobile ? 20 : 24,
                              color:
                                seat.state === SeatState.AVAILABLE
                                  ? 'success.dark'
                                  : 'grey.600',
                            }}
                          />
                          <Typography
                            variant="caption"
                            sx={{
                              fontSize: isMobile ? '0.6rem' : '0.7rem',
                              fontWeight: 'bold',
                            }}
                          >
                            {seat.seatNumber}
                          </Typography>
                        </Paper>
                      </Tooltip>
                    </React.Fragment>
                  );
                })}
              </Box>
            </Box>
          ))}
        </Box>

        <Typography
          variant="body2"
          align="center"
          sx={{ mt: 2, color: 'text.secondary', fontStyle: 'italic' }}
        >
          Rear of Aircraft
        </Typography>
      </Paper>
    </Box>
  );
};
