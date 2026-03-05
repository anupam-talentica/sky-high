import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Box,
  Button,
  Container,
  Typography,
  Paper,
  TextField,
  Alert,
} from '@mui/material';
import PaymentIcon from '@mui/icons-material/Payment';
import { checkinService } from '../services/checkin.service';
import { useCheckinStore } from '../stores/checkinStore';
import { ErrorMessage } from '../components/ErrorMessage';
import type { ApiError } from '../types/api.types';

export const PaymentPage: React.FC = () => {
  const navigate = useNavigate();
  const { checkInId, totalFee, selectedSeat } = useCheckinStore();

  const [cardNumber, setCardNumber] = useState('');
  const [cardName, setCardName] = useState('');
  const [expiryDate, setExpiryDate] = useState('');
  const [cvv, setCvv] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handlePayment = async () => {
    if (!checkInId) return;

    try {
      setLoading(true);
      setError(null);

      // Calculate total amount
      const seatPrice = selectedSeat?.price || 0;
      const totalAmount = seatPrice + totalFee;

      await checkinService.processPayment(checkInId, {
        amount: totalAmount,
        paymentMethod: totalAmount === 0 ? 'FREE' : 'CARD',
        cardNumber: totalAmount === 0 ? undefined : cardNumber,
        cardHolderName: totalAmount === 0 ? undefined : cardName,
      });

      navigate('/checkin/confirmation');
    } catch (err) {
      const apiError = err as ApiError;
      setError(apiError.message);
    } finally {
      setLoading(false);
    }
  };

  const getTotalAmount = () => {
    const seatPrice = selectedSeat?.price || 0;
    return seatPrice + totalFee;
  };

  const isFormValid = () => {
    const totalAmount = getTotalAmount();
    
    // If amount is $0, no card details required
    if (totalAmount === 0) {
      return true;
    }
    
    // Otherwise, validate card details
    return (
      cardNumber.length === 16 &&
      cardName.trim() !== '' &&
      expiryDate.length === 5 &&
      cvv.length === 3
    );
  };

  const formatCardNumber = (value: string) => {
    const cleaned = value.replace(/\D/g, '');
    return cleaned.slice(0, 16);
  };

  const formatExpiryDate = (value: string) => {
    const cleaned = value.replace(/\D/g, '');
    if (cleaned.length >= 2) {
      return `${cleaned.slice(0, 2)}/${cleaned.slice(2, 4)}`;
    }
    return cleaned;
  };

  return (
    <Container maxWidth="md" sx={{ py: 4 }}>
      <Box sx={{ mb: 4 }}>
        <Typography variant="h4" component="h1" gutterBottom>
          Payment
        </Typography>
        <Typography variant="body1" color="text.secondary">
          Complete your payment to confirm check-in
        </Typography>
      </Box>

      {error && <ErrorMessage message={error} />}

      {getTotalAmount() === 0 ? (
        <Alert severity="success" sx={{ mb: 3 }}>
          Great! Your baggage is within the free allowance (20 KG). No payment required.
        </Alert>
      ) : (
        <Alert severity="info" sx={{ mb: 3 }}>
          This is a mock payment page. No actual payment will be processed.
        </Alert>
      )}

      <Paper elevation={2} sx={{ p: 3, mb: 3 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 3 }}>
          <PaymentIcon color="primary" />
          <Typography variant="h6">Payment Summary</Typography>
        </Box>

        <Box sx={{ mb: 3 }}>
          {selectedSeat && selectedSeat.price && (
            <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 1 }}>
              <Typography>Seat {selectedSeat.seatNumber}:</Typography>
              <Typography>${selectedSeat.price.toFixed(2)}</Typography>
            </Box>
          )}
          <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 1 }}>
            <Typography>Baggage Fee:</Typography>
            <Typography>${totalFee.toFixed(2)}</Typography>
          </Box>
          <Box
            sx={{
              display: 'flex',
              justifyContent: 'space-between',
              pt: 2,
              mt: 2,
              borderTop: 1,
              borderColor: 'divider',
            }}
          >
            <Typography variant="h6">Total Amount:</Typography>
            <Typography variant="h6" color="primary">
              ${((selectedSeat?.price || 0) + totalFee).toFixed(2)}
            </Typography>
          </Box>
        </Box>
      </Paper>

      {getTotalAmount() > 0 && (
        <Paper elevation={2} sx={{ p: 3, mb: 3 }}>
          <Typography variant="h6" gutterBottom>
            Card Details
          </Typography>

          <TextField
            fullWidth
            label="Card Number"
            value={cardNumber}
            onChange={(e) => setCardNumber(formatCardNumber(e.target.value))}
            placeholder="1234 5678 9012 3456"
            sx={{ mb: 2 }}
            inputProps={{ maxLength: 16 }}
          />

          <TextField
            fullWidth
            label="Cardholder Name"
            value={cardName}
            onChange={(e) => setCardName(e.target.value)}
            placeholder="John Doe"
            sx={{ mb: 2 }}
          />

          <Box sx={{ display: 'flex', gap: 2 }}>
            <TextField
              label="Expiry Date"
              value={expiryDate}
              onChange={(e) => setExpiryDate(formatExpiryDate(e.target.value))}
              placeholder="MM/YY"
              sx={{ flex: 1 }}
              inputProps={{ maxLength: 5 }}
            />

            <TextField
              label="CVV"
              type="password"
              value={cvv}
              onChange={(e) => setCvv(e.target.value.replace(/\D/g, '').slice(0, 3))}
              placeholder="123"
              sx={{ flex: 1 }}
              inputProps={{ maxLength: 3 }}
            />
          </Box>
        </Paper>
      )}

      <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
        <Button variant="outlined" onClick={() => navigate('/checkin/baggage')}>
          Back
        </Button>
        <Button
          variant="contained"
          onClick={handlePayment}
          disabled={loading || !isFormValid()}
        >
          {loading ? 'Processing...' : getTotalAmount() === 0 ? 'Complete Check-In' : 'Complete Payment'}
        </Button>
      </Box>
    </Container>
  );
};
