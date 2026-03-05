import React from 'react';
import { Alert, AlertTitle, Snackbar } from '@mui/material';

interface SuccessMessageProps {
  title?: string;
  message: string;
  open: boolean;
  onClose: () => void;
  autoHideDuration?: number;
}

export const SuccessMessage: React.FC<SuccessMessageProps> = ({
  title = 'Success',
  message,
  open,
  onClose,
  autoHideDuration = 6000,
}) => {
  return (
    <Snackbar
      open={open}
      autoHideDuration={autoHideDuration}
      onClose={onClose}
      anchorOrigin={{ vertical: 'top', horizontal: 'center' }}
    >
      <Alert onClose={onClose} severity="success" sx={{ width: '100%' }}>
        <AlertTitle>{title}</AlertTitle>
        {message}
      </Alert>
    </Snackbar>
  );
};
