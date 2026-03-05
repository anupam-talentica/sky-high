import React from 'react';
import { Outlet, useNavigate } from 'react-router-dom';
import {
  AppBar,
  Box,
  Button,
  IconButton,
  Toolbar,
  Typography,
  useTheme,
  useMediaQuery,
} from '@mui/material';
import FlightTakeoffIcon from '@mui/icons-material/FlightTakeoff';
import LogoutIcon from '@mui/icons-material/Logout';
import { useAuthStore } from '../stores/authStore';
import { authService } from '../services/auth.service';

export const Layout: React.FC = () => {
  const navigate = useNavigate();
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down('sm'));
  const { isAuthenticated, name, clearAuth } = useAuthStore();

  const handleLogout = () => {
    clearAuth();
    authService.logout();
  };

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', minHeight: '100vh' }}>
      <AppBar position="static" elevation={2}>
        <Toolbar>
          <IconButton
            size="large"
            edge="start"
            color="inherit"
            sx={{ mr: 2 }}
            onClick={() => navigate('/')}
          >
            <FlightTakeoffIcon />
          </IconButton>
          <Typography
            variant="h6"
            component="div"
            sx={{ flexGrow: 1, cursor: 'pointer' }}
            onClick={() => navigate('/')}
          >
            SkyHigh
          </Typography>
          {isAuthenticated && (
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
              {!isMobile && name && (
                <Typography variant="body2">Welcome, {name}</Typography>
              )}
              <Button
                color="inherit"
                startIcon={<LogoutIcon />}
                onClick={handleLogout}
                size={isMobile ? 'small' : 'medium'}
              >
                {isMobile ? '' : 'Logout'}
              </Button>
            </Box>
          )}
        </Toolbar>
      </AppBar>
      <Box component="main" sx={{ flexGrow: 1, backgroundColor: 'grey.50' }}>
        <Outlet />
      </Box>
      <Box
        component="footer"
        sx={{
          py: 3,
          px: 2,
          mt: 'auto',
          backgroundColor: 'grey.200',
          textAlign: 'center',
        }}
      >
        <Typography variant="body2" color="text.secondary">
          © 2026 SkyHigh Airlines. All rights reserved.
        </Typography>
      </Box>
    </Box>
  );
};
