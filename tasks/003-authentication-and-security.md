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
- [ ] Create JwtTokenProvider class for token generation
- [ ] Create JwtAuthenticationFilter for token validation
- [ ] Configure JWT secret in environment variables (min 256 bits)
- [ ] Set token expiration to 1 hour (3600000 ms)
- [ ] Use HS512 algorithm for signing
- [ ] Extract passenger ID from token claims

### 2. Spring Security Configuration
- [ ] Create SecurityConfig class
- [ ] Configure authentication manager
- [ ] Define public endpoints (/api/v1/auth/login, /actuator/health)
- [ ] Protect all other endpoints with JWT authentication
- [ ] Configure password encoder (BCrypt)

### 3. Authentication Controller
- [ ] Create AuthController with login endpoint
- [ ] Implement POST /api/v1/auth/login
- [ ] Validate credentials against hardcoded users
- [ ] Return JWT token and passenger info on success
- [ ] Handle authentication failures with proper error messages

### 4. Hardcoded Users (MVP)
- [ ] Store users in application.yml or database
- [ ] User 1: P123456, john@example.com, password: demo123
- [ ] User 2: P789012, jane@example.com, password: demo456
- [ ] Hash passwords with BCrypt before storage

### 5. Security Best Practices
- [ ] Configure CORS to allow only frontend URL
- [ ] Add input validation on all request parameters
- [ ] Implement rate limiting with Bucket4j
- [ ] Configure security headers (X-Content-Type-Options, X-Frame-Options)
- [ ] Ensure all API communication over HTTPS
- [ ] Mask sensitive data in logs

### 6. Error Handling
- [ ] Create custom exception for authentication failures
- [ ] Return 401 for invalid credentials
- [ ] Return 401 for expired tokens
- [ ] Return 403 for unauthorized access
- [ ] Implement global exception handler

## Dependencies
- Spring Security dependency added
- JWT library (io.jsonwebtoken:jjwt) added
- Bucket4j for rate limiting

## Success Criteria
- Users can login with valid credentials and receive JWT token
- Invalid credentials return 401 error
- Protected endpoints require valid JWT token
- Expired tokens are rejected
- CORS is properly configured
- Rate limiting prevents abuse
- Passwords are securely hashed

## Estimated Effort
High-level authentication setup task

## References
- TRD.md Section 6: Security & Authentication
- PRD.md Section 8: Security Requirements
