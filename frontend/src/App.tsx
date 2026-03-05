import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { ThemeProvider, createTheme, CssBaseline } from '@mui/material';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ErrorBoundary } from './components/ErrorBoundary';
import { ProtectedRoute } from './components/ProtectedRoute';
import { Layout } from './components/Layout';
import { LoginPage } from './pages/LoginPage';
import { FlightSelectionPage } from './pages/FlightSelectionPage';
import { SeatSelectionPage } from './pages/SeatSelectionPage';
import { BaggagePage } from './pages/BaggagePage';
import { PaymentPage } from './pages/PaymentPage';
import { ConfirmationPage } from './pages/ConfirmationPage';
import { WaitlistPage } from './pages/WaitlistPage';
import { NotFoundPage } from './pages/NotFoundPage';

const theme = createTheme({
  palette: {
    primary: {
      main: '#1976d2',
    },
    secondary: {
      main: '#dc004e',
    },
  },
  typography: {
    fontFamily: '"Roboto", "Helvetica", "Arial", sans-serif',
  },
});

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      refetchOnWindowFocus: false,
      retry: 1,
    },
  },
});

function App() {
  return (
    <ErrorBoundary>
      <QueryClientProvider client={queryClient}>
        <ThemeProvider theme={theme}>
          <CssBaseline />
          <BrowserRouter>
            <Routes>
              <Route path="/login" element={<LoginPage />} />
              <Route element={<Layout />}>
                <Route path="/" element={<Navigate to="/flights" replace />} />
                <Route
                  path="/flights"
                  element={
                    <ProtectedRoute>
                      <FlightSelectionPage />
                    </ProtectedRoute>
                  }
                />
                <Route
                  path="/checkin/seat-selection"
                  element={
                    <ProtectedRoute>
                      <SeatSelectionPage />
                    </ProtectedRoute>
                  }
                />
                <Route
                  path="/checkin/baggage"
                  element={
                    <ProtectedRoute>
                      <BaggagePage />
                    </ProtectedRoute>
                  }
                />
                <Route
                  path="/checkin/payment"
                  element={
                    <ProtectedRoute>
                      <PaymentPage />
                    </ProtectedRoute>
                  }
                />
                <Route
                  path="/checkin/confirmation"
                  element={
                    <ProtectedRoute>
                      <ConfirmationPage />
                    </ProtectedRoute>
                  }
                />
                <Route
                  path="/checkin/waitlist"
                  element={
                    <ProtectedRoute>
                      <WaitlistPage />
                    </ProtectedRoute>
                  }
                />
                <Route path="*" element={<NotFoundPage />} />
              </Route>
            </Routes>
          </BrowserRouter>
        </ThemeProvider>
      </QueryClientProvider>
    </ErrorBoundary>
  );
}

export default App;
