import React from 'react';
import { Alert, AlertTitle, Box } from '@mui/material';

interface ErrorMessageProps {
  title?: string;
  message: string;
  onRetry?: () => void;
}

export const ErrorMessage: React.FC<ErrorMessageProps> = ({ title = 'Error', message, onRetry }) => {
  return (
    <Box sx={{ width: '100%', my: 2 }}>
      <Alert 
        severity="error" 
        onClose={onRetry ? onRetry : undefined}
        action={onRetry ? undefined : null}
      >
        <AlertTitle>{title}</AlertTitle>
        {message}
      </Alert>
    </Box>
  );
};
