import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Box,
  Button,
  Container,
  Typography,
  Paper,
  TextField,
  Select,
  MenuItem,
  FormControl,
  InputLabel,
  List,
  ListItem,
  ListItemText,
  IconButton,
  Alert,
} from '@mui/material';
import DeleteIcon from '@mui/icons-material/Delete';
import LuggageIcon from '@mui/icons-material/Luggage';
import { checkinService } from '../services/checkin.service';
import { useCheckinStore } from '../stores/checkinStore';
import { ErrorMessage } from '../components/ErrorMessage';
import type { ApiError } from '../types/api.types';

export const BaggagePage: React.FC = () => {
  const navigate = useNavigate();
  const { checkInId, baggageItems, addBaggageItem, setBaggageItems, removeBaggageItem, totalFee } =
    useCheckinStore();

  const [weight, setWeight] = useState<string>('');
  const [unit, setUnit] = useState<string>('kg');
  const [type, setType] = useState<'carry_on' | 'checked'>('checked');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [initialLoadDone, setInitialLoadDone] = useState(false);

  useEffect(() => {
    if (!checkInId || initialLoadDone) return;
    
    const loadExistingBaggage = async () => {
      try {
        setLoading(true);
        const existingBaggage = await checkinService.getBaggage(checkInId);
        // Replace store with API state so we don't duplicate when user navigates back from seat selection
        const items = existingBaggage.map((item) => ({
          baggageId: item.baggageId,
          weight: item.weightKg,
          unit: 'kg' as const,
          type: (item.baggageType === 'CARRY_ON' ? 'carry_on' : 'checked') as 'carry_on' | 'checked',
          fee: item.excessFee,
        }));
        setBaggageItems(items);
        setInitialLoadDone(true);
      } catch (err) {
        console.error('Failed to load existing baggage:', err);
      } finally {
        setLoading(false);
      }
    };
    
    loadExistingBaggage();
  }, [checkInId, initialLoadDone]);

  const handleAddBaggage = async () => {
    if (!checkInId || !weight) return;

    try {
      setLoading(true);
      setError(null);

      // Convert weight to kg if needed
      let weightInKg = parseFloat(weight);
      if (unit === 'lb') {
        weightInKg = weightInKg * 0.453592; // Convert pounds to kg
      }

      // Map frontend type to backend enum
      const baggageTypeMap: Record<string, 'CARRY_ON' | 'CHECKED'> = {
        'carry_on': 'CARRY_ON',
        'checked': 'CHECKED',
      };

      const response = await checkinService.addBaggage(checkInId, {
        weightKg: weightInKg,
        baggageType: baggageTypeMap[type],
      });

      addBaggageItem({
        baggageId: response.baggageId,
        weight: response.weightKg,
        unit: 'kg',
        type: response.baggageType === 'CARRY_ON' ? 'carry_on' : 'checked',
        fee: response.excessFee,
      });

      setWeight('');
      setType('checked');
    } catch (err) {
      const apiError = err as ApiError;
      setError(apiError.message);
    } finally {
      setLoading(false);
    }
  };

  const handleRemoveBaggage = async (index: number) => {
    const item = baggageItems[index];
    if (!item.baggageId || !checkInId) return;

    try {
      setLoading(true);
      await checkinService.deleteBaggage(checkInId, item.baggageId);
      removeBaggageItem(index);
    } catch (err) {
      const apiError = err as ApiError;
      setError(apiError.message);
    } finally {
      setLoading(false);
    }
  };

  const handleContinue = () => {
    // If total fee is $0, go directly to confirmation/review page
    // Otherwise, go to payment page
    if (totalFee === 0) {
      navigate('/checkin/confirmation');
    } else {
      navigate('/checkin/payment');
    }
  };

  const handleSkip = () => {
    // No baggage means $0 fee, go directly to confirmation
    navigate('/checkin/confirmation');
  };

  return (
    <Container maxWidth="md" sx={{ py: 4 }}>
      <Box sx={{ mb: 4 }}>
        <Typography variant="h4" component="h1" gutterBottom>
          Add Baggage
        </Typography>
        <Typography variant="body1" color="text.secondary">
          Add your baggage details (optional)
        </Typography>
      </Box>

      {error && <ErrorMessage message={error} />}

      <Paper elevation={2} sx={{ p: 3, mb: 3 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 3 }}>
          <LuggageIcon color="primary" />
          <Typography variant="h6">Baggage Details</Typography>
        </Box>

        <Box sx={{ display: 'flex', gap: 2, flexWrap: 'wrap', mb: 3 }}>
          <TextField
            label="Weight"
            type="number"
            value={weight}
            onChange={(e) => setWeight(e.target.value)}
            sx={{ flex: 1, minWidth: 120 }}
            inputProps={{ min: 0, step: 0.1 }}
          />

          <FormControl sx={{ flex: 1, minWidth: 120 }}>
            <InputLabel>Unit</InputLabel>
            <Select value={unit} label="Unit" onChange={(e) => setUnit(e.target.value)}>
              <MenuItem value="kg">Kilograms (kg)</MenuItem>
              <MenuItem value="lb">Pounds (lb)</MenuItem>
            </Select>
          </FormControl>

          <FormControl sx={{ flex: 1, minWidth: 120 }}>
            <InputLabel>Type</InputLabel>
            <Select
              value={type}
              label="Type"
              onChange={(e) => setType(e.target.value as 'carry_on' | 'checked')}
            >
              <MenuItem value="carry_on">Carry-On</MenuItem>
              <MenuItem value="checked">Checked</MenuItem>
            </Select>
          </FormControl>
        </Box>

        <Button
          variant="outlined"
          onClick={handleAddBaggage}
          disabled={loading || !weight}
          fullWidth
        >
          Add Baggage
        </Button>
      </Paper>

      {baggageItems.length > 0 && (
        <Paper elevation={2} sx={{ p: 3, mb: 3 }}>
          <Typography variant="h6" gutterBottom>
            Added Baggage
          </Typography>
          <List>
            {baggageItems.map((item, index) => (
              <ListItem
                key={index}
                secondaryAction={
                  <IconButton edge="end" onClick={() => handleRemoveBaggage(index)}>
                    <DeleteIcon />
                  </IconButton>
                }
              >
              <ListItemText
                primary={`${item.type === 'carry_on' ? 'Carry-On' : 'Checked'} - ${item.weight} ${item.unit}`}
                secondary={`Fee: $${(item.fee || 0).toFixed(2)}`}
              />
              </ListItem>
            ))}
          </List>

          <Box
            sx={{
              mt: 2,
              pt: 2,
              borderTop: 1,
              borderColor: 'divider',
              display: 'flex',
              justifyContent: 'space-between',
            }}
          >
            <Typography variant="h6">Total Baggage Fee:</Typography>
            <Typography variant="h6" color="primary">
              ${(totalFee || 0).toFixed(2)}
            </Typography>
          </Box>
        </Paper>
      )}

      {baggageItems.length === 0 && (
        <Alert severity="info" sx={{ mb: 3 }}>
          No baggage added. You can skip this step if you don't have any baggage to check.
        </Alert>
      )}

      <Box sx={{ display: 'flex', justifyContent: 'space-between', gap: 2 }}>
        <Button variant="outlined" onClick={() => navigate('/checkin/seat-selection')}>
          Back
        </Button>
        <Box sx={{ display: 'flex', gap: 2 }}>
          {baggageItems.length === 0 && (
            <Button variant="text" onClick={handleSkip}>
              Skip
            </Button>
          )}
          <Button variant="contained" onClick={handleContinue}>
            {totalFee === 0 ? 'Review & Confirm' : 'Continue to Payment'}
          </Button>
        </Box>
      </Box>
    </Container>
  );
};
