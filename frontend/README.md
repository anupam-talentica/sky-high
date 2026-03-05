# SkyHigh Core - Frontend

React TypeScript frontend application for the SkyHigh digital check-in system.

## Technology Stack

- **Framework**: React 19
- **Language**: TypeScript
- **Build Tool**: Vite
- **UI Library**: Material-UI (MUI)
- **State Management**: Zustand
- **HTTP Client**: Axios
- **Routing**: React Router v7
- **Data Fetching**: TanStack Query (React Query)

## Project Structure

```
frontend/
├── src/
│   ├── components/        # Reusable UI components
│   │   ├── ErrorBoundary.tsx
│   │   ├── ErrorMessage.tsx
│   │   ├── Layout.tsx
│   │   ├── Loading.tsx
│   │   ├── ProtectedRoute.tsx
│   │   ├── SeatMap.tsx
│   │   └── SuccessMessage.tsx
│   ├── pages/            # Page-level components
│   │   ├── BaggagePage.tsx
│   │   ├── ConfirmationPage.tsx
│   │   ├── FlightSelectionPage.tsx
│   │   ├── LoginPage.tsx
│   │   ├── NotFoundPage.tsx
│   │   ├── PaymentPage.tsx
│   │   ├── SeatSelectionPage.tsx
│   │   └── WaitlistPage.tsx
│   ├── services/         # API client services
│   │   ├── api.client.ts
│   │   ├── auth.service.ts
│   │   ├── checkin.service.ts
│   │   ├── flight.service.ts
│   │   ├── seat.service.ts
│   │   └── waitlist.service.ts
│   ├── stores/           # Zustand state stores
│   │   ├── authStore.ts
│   │   └── checkinStore.ts
│   ├── types/            # TypeScript type definitions
│   │   ├── api.types.ts
│   │   ├── auth.types.ts
│   │   ├── checkin.types.ts
│   │   ├── flight.types.ts
│   │   ├── seat.types.ts
│   │   └── waitlist.types.ts
│   ├── utils/            # Utility functions
│   │   ├── date.util.ts
│   │   ├── format.util.ts
│   │   └── validation.util.ts
│   ├── App.tsx           # Main application component
│   ├── main.tsx          # Application entry point
│   └── index.css         # Global styles
├── public/               # Static assets
├── .env.development      # Development environment variables
├── .env.example          # Environment variables template
├── package.json          # Dependencies and scripts
├── tsconfig.json         # TypeScript configuration
├── vite.config.ts        # Vite configuration
└── Dockerfile            # Docker configuration

```

## Prerequisites

- Node.js 18+ and npm
- Backend API running on `http://localhost:8080`

## Environment Variables

Create a `.env.development` file in the frontend directory:

```env
VITE_API_BASE_URL=http://localhost:8080/api/v1
VITE_APP_NAME=SkyHigh Core
VITE_APP_VERSION=1.0.0
```

For production, create `.env.production`:

```env
VITE_API_BASE_URL=https://api.skyhigh.com/api/v1
VITE_APP_NAME=SkyHigh Core
VITE_APP_VERSION=1.0.0
```

## Installation

```bash
cd frontend
npm install
```

## Development

Start the development server:

```bash
npm run dev
```

The application will be available at `http://localhost:5173`

## Build

Build for production:

```bash
npm run build
```

The built files will be in the `dist/` directory.

## Preview Production Build

Preview the production build locally:

```bash
npm run preview
```

## Features

### Authentication
- Login with email and password
- JWT token storage in localStorage
- Automatic token refresh
- Protected routes with authentication check

### Flight Selection
- Browse available flights
- View flight details (origin, destination, times)
- See available seat count
- Initiate check-in for selected flight

### Seat Selection
- Interactive seat map visualization
- Color-coded seat states (available, held, confirmed, unavailable)
- Seat reservation with 120-second hold timer
- Real-time seat availability updates
- Waitlist option for unavailable seats

### Baggage Management
- Add multiple baggage items
- Specify weight, unit, and type (carry-on/checked)
- Calculate baggage fees
- Remove baggage items

### Payment
- Mock payment form
- Card validation
- Payment summary with itemized costs

### Confirmation
- Display boarding pass
- Download boarding pass
- Check-in summary

### Waitlist
- View active waitlist entries
- Real-time position updates
- Leave waitlist option
- Notification when seat becomes available

## Key Components

### SeatMap Component
Interactive seat map with:
- Grid layout matching aircraft configuration
- Color-coded seat states
- Hover tooltips with seat details
- Click to select available seats
- Responsive design for mobile

### Protected Routes
Wrapper component that:
- Checks authentication status
- Redirects to login if not authenticated
- Preserves intended destination

### Error Boundary
Global error handler that:
- Catches React component errors
- Displays user-friendly error page
- Provides option to return home

### API Client
Centralized Axios instance with:
- Automatic JWT token injection
- Request/response interceptors
- Error handling and transformation
- Automatic redirect on 401 errors

## State Management

### Auth Store (Zustand)
- User authentication state
- Token management
- Persistent storage in localStorage

### Check-In Store (Zustand)
- Check-in flow state
- Selected seat tracking
- Baggage items
- Current step in workflow
- Seat hold timer

## API Integration

All API calls go through service modules:
- `auth.service.ts` - Authentication
- `flight.service.ts` - Flight operations
- `seat.service.ts` - Seat management
- `checkin.service.ts` - Check-in workflow
- `waitlist.service.ts` - Waitlist operations

## Responsive Design

The application is fully responsive with:
- Mobile-first approach
- MUI breakpoints for different screen sizes
- Optimized layouts for mobile, tablet, and desktop
- Touch-friendly interactions

## Error Handling

Comprehensive error handling:
- API error messages displayed to users
- Network error detection
- Validation errors
- Global error boundary for uncaught errors
- Retry mechanisms for failed requests

## Performance Optimizations

- Code splitting with React.lazy()
- Lazy loading of routes
- Optimized bundle size
- React Query for efficient data fetching
- Memoization of expensive computations

## Browser Support

- Chrome (latest)
- Firefox (latest)
- Safari (latest)
- Edge (latest)

## Docker

Build Docker image:

```bash
docker build -t skyhigh-frontend .
```

Run container:

```bash
docker run -p 80:80 skyhigh-frontend
```

## Deployment

The frontend can be deployed to:
- AWS S3 + CloudFront (recommended)
- Netlify
- Vercel
- Any static hosting service

Build the production bundle and upload the `dist/` directory contents.

## Troubleshooting

### CORS Issues
Ensure the backend API has CORS configured to allow requests from the frontend origin.

### API Connection Failed
- Check that the backend is running
- Verify `VITE_API_BASE_URL` in `.env.development`
- Check browser console for network errors

### Authentication Issues
- Clear localStorage and try logging in again
- Check JWT token expiration
- Verify backend authentication endpoints

## License

Copyright © 2026 SkyHigh Airlines. All rights reserved.
