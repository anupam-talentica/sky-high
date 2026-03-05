# Authentication & Security

## Overview

The SkyHigh Core application uses JWT (JSON Web Token) based authentication with Spring Security. This document describes the authentication implementation, security configuration, and usage.

## Architecture

### Components

1. **JwtTokenProvider**: Generates and validates JWT tokens
2. **JwtAuthenticationFilter**: Intercepts requests and validates JWT tokens
3. **JwtAuthenticationEntryPoint**: Handles unauthorized access attempts
4. **SecurityConfig**: Configures Spring Security and CORS
5. **UserService**: Manages hardcoded users (MVP)
6. **AuthenticationService**: Handles login logic
7. **AuthController**: Exposes authentication endpoints

## JWT Configuration

### Token Details

- **Algorithm**: HS512 (HMAC with SHA-512)
- **Expiration**: 1 hour (3600000 ms)
- **Secret**: Configured via `JWT_SECRET` environment variable
- **Claims**: 
  - `subject`: Passenger ID
  - `email`: User email
  - `iat`: Issued at timestamp
  - `exp`: Expiration timestamp

### Environment Variables

```bash
JWT_SECRET=your-256-bit-secret-key-change-this-in-production-please-make-it-long
```

**Important**: The JWT secret must be at least 256 bits (32 characters) for HS512 algorithm.

## Hardcoded Users (MVP)

For the MVP, users are hardcoded in the `UserService` class:

| Passenger ID | Email | Password | Name |
|--------------|-------|----------|------|
| P123456 | john@example.com | demo123 | John Doe |
| P789012 | jane@example.com | demo456 | Jane Smith |

Passwords are hashed using BCrypt before storage.

## API Endpoints

### Login

**Endpoint**: `POST /api/v1/auth/login`

**Request**:
```json
{
  "email": "john@example.com",
  "password": "demo123"
}
```

**Response** (200 OK):
```json
{
  "token": "eyJhbGciOiJIUzUxMiJ9...",
  "tokenType": "Bearer",
  "passengerId": "P123456",
  "email": "john@example.com",
  "name": "John Doe"
}
```

**Error Response** (401 Unauthorized):
```json
{
  "status": 401,
  "error": "Unauthorized",
  "message": "Invalid email or password",
  "path": "/api/v1/auth/login",
  "timestamp": "2026-02-27T12:00:00"
}
```

### Health Check

**Endpoint**: `GET /api/v1/auth/health`

**Response** (200 OK):
```
Authentication service is running
```

## Using JWT Tokens

### Making Authenticated Requests

Include the JWT token in the `Authorization` header:

```bash
curl -H "Authorization: Bearer eyJhbGciOiJIUzUxMiJ9..." \
     http://localhost:8080/api/v1/flights/FL123/seat-map
```

### Token Validation

The `JwtAuthenticationFilter` automatically:
1. Extracts the token from the `Authorization` header
2. Validates the token signature and expiration
3. Extracts the passenger ID from token claims
4. Sets the authentication in Spring Security context

## Security Configuration

### Protected Endpoints

All endpoints except the following require authentication:
- `/api/v1/auth/**` - Authentication endpoints
- `/actuator/health` - Health check endpoint
- `/actuator/info` - Application info endpoint

### CORS Configuration

CORS is configured to allow requests from:
- `http://localhost:3000` (React default)
- `http://localhost:5173` (Vite default)
- `http://localhost:8080` (Backend)

Allowed methods: `GET`, `POST`, `PUT`, `DELETE`, `OPTIONS`

### Security Headers

The following security headers are configured:
- `X-Content-Type-Options: nosniff`
- `X-XSS-Protection: 1; mode=block`
- `X-Frame-Options: DENY`

### Password Hashing

Passwords are hashed using BCrypt with Spring Security's `BCryptPasswordEncoder`:
- Automatically generates salt
- Uses adaptive hashing (cost factor: 10)
- Resistant to rainbow table attacks

## Error Handling

### Global Exception Handler

The `GlobalExceptionHandler` handles authentication-related exceptions:

| Exception | HTTP Status | Description |
|-----------|-------------|-------------|
| `AuthenticationFailedException` | 401 | Invalid credentials |
| `UnauthorizedException` | 401 | Missing or invalid token |
| `AccessDeniedException` | 403 | Insufficient permissions |
| `MethodArgumentNotValidException` | 400 | Validation errors |

### Common Error Scenarios

1. **Invalid Credentials**: Returns 401 with "Invalid email or password"
2. **Expired Token**: Returns 401 with "Expired JWT token"
3. **Malformed Token**: Returns 401 with "Invalid JWT token"
4. **Missing Token**: Returns 401 with "Authentication required"

## Testing

### Unit Tests

- `JwtTokenProviderTest`: Tests token generation and validation
- `UserServiceTest`: Tests user lookup and credential validation
- `AuthenticationServiceTest`: Tests login logic
- `AuthControllerTest`: Tests authentication endpoints

### Integration Tests

- `AuthControllerIntegrationTest`: Tests complete authentication flow

### Running Tests

```bash
# Run all authentication tests
mvn test -Dtest=*Auth*,*Jwt*,UserServiceTest

# Run specific test
mvn test -Dtest=AuthControllerIntegrationTest
```

## Migration to Database-Driven Authentication

To migrate from hardcoded users to database-driven authentication:

1. Create `User` entity with JPA annotations
2. Create `UserRepository` extending `JpaRepository`
3. Update `UserService` to use repository instead of in-memory map
4. Add user registration endpoint
5. Implement password reset functionality
6. Add refresh token support

## Security Best Practices

1. **Never log JWT tokens** - Tokens should be treated as sensitive data
2. **Use HTTPS in production** - All API communication must be over TLS
3. **Rotate JWT secret regularly** - Change the secret periodically
4. **Implement token refresh** - Add refresh token mechanism for production
5. **Add rate limiting** - Prevent brute force attacks on login endpoint
6. **Monitor failed login attempts** - Log and alert on suspicious activity
7. **Validate all inputs** - Use Bean Validation annotations on DTOs

## Troubleshooting

### Common Issues

**Issue**: "Invalid JWT signature"
- **Cause**: JWT secret mismatch between token generation and validation
- **Solution**: Ensure `JWT_SECRET` is consistent across all instances

**Issue**: "Expired JWT token"
- **Cause**: Token expiration time has passed
- **Solution**: Request a new token by logging in again

**Issue**: "No qualifying bean of type 'JwtTokenProvider'"
- **Cause**: Component scanning not configured properly
- **Solution**: Ensure `@Component` annotation is present and package is scanned

**Issue**: "CORS error in browser"
- **Cause**: Frontend origin not in allowed origins list
- **Solution**: Add frontend URL to CORS configuration in `SecurityConfig`

## References

- [JWT.io](https://jwt.io/) - JWT token debugger
- [Spring Security Documentation](https://docs.spring.io/spring-security/reference/)
- [OWASP Authentication Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html)
