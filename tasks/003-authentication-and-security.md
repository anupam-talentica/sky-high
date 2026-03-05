# Task 003: Authentication and Security

## Objective
Implement JWT-based authentication system with Spring Security and configure security best practices.

## Scope
- Implement JWT token generation and validation
- Configure Spring Security
- Create authentication endpoints
- Implement password hashing
- Configure CORS and security headers

## Key Deliverables

### 1. JWT Implementation
- [x] Create JwtTokenProvider class for token generation
- [x] Create JwtAuthenticationFilter for token validation
- [x] Configure JWT secret in environment variables (min 256 bits)
- [x] Set token expiration to 1 hour (3600000 ms)
- [x] Use HS512 algorithm for signing
- [x] Extract passenger ID from token claims

### 2. Spring Security Configuration
- [x] Create SecurityConfig class
- [x] Configure authentication manager
- [x] Define public endpoints (/api/v1/auth/login, /actuator/health)
- [x] Protect all other endpoints with JWT authentication
- [x] Configure password encoder (BCrypt)

### 3. Authentication Controller
- [x] Create AuthController with login endpoint
- [x] Implement POST /api/v1/auth/login
- [x] Validate credentials against hardcoded users
- [x] Return JWT token and passenger info on success
- [x] Handle authentication failures with proper error messages

### 4. Hardcoded Users (MVP)
- [x] Store users in application.yml or database
- [x] User 1: P123456, john@example.com, password: demo123
- [x] User 2: P789012, jane@example.com, password: demo456
- [x] Hash passwords with BCrypt before storage

### 5. Security Best Practices
- [x] Configure CORS to allow only frontend URL
- [x] Add input validation on all request parameters
- [ ] Implement rate limiting with Bucket4j (deferred to production)
- [x] Configure security headers (X-Content-Type-Options, X-Frame-Options)
- [x] Ensure all API communication over HTTPS (configured, enforced in deployment)
- [x] Mask sensitive data in logs (implemented in logging configuration)

### 6. Error Handling
- [x] Create custom exception for authentication failures
- [x] Return 401 for invalid credentials
- [x] Return 401 for expired tokens
- [x] Return 403 for unauthorized access
- [x] Implement global exception handler

## Dependencies
- [x] Spring Security dependency added
- [x] JWT library (io.jsonwebtoken:jjwt) added
- [ ] Bucket4j for rate limiting (deferred to production)

## Success Criteria
- [x] Users can login with valid credentials and receive JWT token
- [x] Invalid credentials return 401 error
- [x] Protected endpoints require valid JWT token
- [x] Expired tokens are rejected
- [x] CORS is properly configured
- [ ] Rate limiting prevents abuse (deferred to production)
- [x] Passwords are securely hashed

## Implementation Summary

### Files Created

**Security Components**:
- `backend/src/main/java/com/skyhigh/security/JwtTokenProvider.java` - JWT token generation and validation
- `backend/src/main/java/com/skyhigh/security/JwtAuthenticationFilter.java` - Request interceptor for JWT validation
- `backend/src/main/java/com/skyhigh/security/JwtAuthenticationEntryPoint.java` - Handles unauthorized access
- `backend/src/main/java/com/skyhigh/security/User.java` - User model for hardcoded authentication

**Configuration**:
- `backend/src/main/java/com/skyhigh/config/SecurityConfig.java` - Spring Security and CORS configuration

**Services**:
- `backend/src/main/java/com/skyhigh/service/UserService.java` - Manages hardcoded users
- `backend/src/main/java/com/skyhigh/service/AuthenticationService.java` - Authentication logic

**Controllers**:
- `backend/src/main/java/com/skyhigh/controller/AuthController.java` - Authentication endpoints

**DTOs**:
- `backend/src/main/java/com/skyhigh/dto/LoginRequest.java` - Login request DTO
- `backend/src/main/java/com/skyhigh/dto/LoginResponse.java` - Login response DTO
- `backend/src/main/java/com/skyhigh/dto/ErrorResponse.java` - Error response DTO

**Exceptions**:
- `backend/src/main/java/com/skyhigh/exception/AuthenticationFailedException.java` - Authentication failure exception
- `backend/src/main/java/com/skyhigh/exception/UnauthorizedException.java` - Unauthorized access exception
- `backend/src/main/java/com/skyhigh/exception/GlobalExceptionHandler.java` - Global exception handler

**Tests**:
- `backend/src/test/java/com/skyhigh/security/JwtTokenProviderTest.java` - JWT provider unit tests
- `backend/src/test/java/com/skyhigh/service/UserServiceTest.java` - User service unit tests
- `backend/src/test/java/com/skyhigh/service/AuthenticationServiceTest.java` - Authentication service unit tests
- `backend/src/test/java/com/skyhigh/controller/AuthControllerTest.java` - Auth controller unit tests
- `backend/src/test/java/com/skyhigh/controller/AuthControllerIntegrationTest.java` - Integration tests
- `backend/src/test/resources/application-test.yml` - Test configuration

**Documentation**:
- `backend/AUTHENTICATION.md` - Comprehensive authentication documentation

### Configuration Changes

**application.yml**:
- JWT secret configuration: `spring.security.jwt.secret`
- JWT expiration: 1 hour (3600000 ms)
- CORS allowed origins configured

### Test Results

All tests passing:
- 8 tests in JwtTokenProviderTest ✓
- 7 tests in UserServiceTest ✓
- 3 tests in AuthenticationServiceTest ✓
- 6 tests in AuthControllerTest ✓
- 3 tests in AuthControllerIntegrationTest ✓

**Total: 27 tests passed**

### Notes

- Rate limiting with Bucket4j is deferred to production deployment
- HTTPS enforcement is configured but will be enforced at deployment level
- Hardcoded users are suitable for MVP; migration path to database-driven auth documented
- All passwords are BCrypt hashed with salt
- JWT tokens expire after 1 hour as per requirements

## Estimated Effort
High-level authentication setup task

## References
- TRD.md Section 6: Security & Authentication
- PRD.md Section 8: Security Requirements
