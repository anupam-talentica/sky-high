# AviationStack API Integration - Implementation Task

**Feature**: FR-009 - Real-Time Flight Status Integration  
**Priority**: P1 (High)  
**Estimated Effort**: 8-12 hours  
**Status**: Not Started  
**Owner**: Backend Team  
**Created**: February 27, 2026

---

## Table of Contents

1. [Overview](#1-overview)
2. [Prerequisites](#2-prerequisites)
3. [Implementation Tasks](#3-implementation-tasks)
4. [Testing Requirements](#4-testing-requirements)
5. [Deployment Checklist](#5-deployment-checklist)
6. [Success Criteria](#6-success-criteria)

---

## 1. Overview

### 1.1 Objective
Integrate AviationStack API to provide real-time flight status, gate information, and delay notifications during the check-in process.

### 1.2 Business Value
- Passengers see up-to-date flight information during check-in
- Prevents check-ins for cancelled flights
- Displays gate changes and delays prominently
- Enhances passenger experience with real-time data

### 1.3 Technical Scope
- Create service layer for AviationStack API integration
- Implement caching to reduce API calls
- Add circuit breaker and retry logic for resilience
- Update check-in flow to fetch and display flight status
- Create fallback mechanism using local database

---

## 2. Prerequisites

### 2.1 API Access
- [ ] Sign up for AviationStack account at https://aviationstack.com/
- [ ] Obtain API key from dashboard
- [ ] Verify free tier limits: 100 calls/month
- [ ] Test API key with sample request
- [ ] Store API key in environment variables (never commit to repo)

### 2.2 Dependencies
Add to `pom.xml`:
```xml
<!-- Resilience4j for Circuit Breaker -->
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-spring-boot3</artifactId>
    <version>2.1.0</version>
</dependency>

<!-- WebClient for HTTP calls -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
</dependency>
```

### 2.3 Configuration
Add to `application.yml`:
```yaml
external:
  aviationstack:
    api-key: ${AVIATIONSTACK_API_KEY}
    base-url: http://api.aviationstack.com/v1
    timeout: 5000
    enabled: true

spring:
  cache:
    caffeine:
      spec: maximumSize=500,expireAfterWrite=300s

resilience4j:
  circuitbreaker:
    instances:
      aviationstack:
        failure-rate-threshold: 50
        wait-duration-in-open-state: 60000
        sliding-window-size: 10
        minimum-number-of-calls: 5
  retry:
    instances:
      aviationstack:
        max-attempts: 3
        wait-duration: 1000
        exponential-backoff-multiplier: 2
```

---

## 3. Implementation Tasks

### 3.1 Backend Implementation

#### Task 3.1.1: Create DTOs (Data Transfer Objects)
**File**: `backend/src/main/java/com/skyhigh/dto/FlightStatusDTO.java`

**Subtasks**:
- [ ] Create `AviationStackResponse.java` - Maps to AviationStack API response
- [ ] Create `FlightStatusDTO.java` - Internal representation for frontend
- [ ] Create `DepartureInfoDTO.java` - Departure details
- [ ] Create `ArrivalInfoDTO.java` - Arrival details
- [ ] Create `DelayInfoDTO.java` - Delay information
- [ ] Add validation annotations (@NotNull, @Pattern, etc.)
- [ ] Add Lombok annotations (@Data, @Builder, @NoArgsConstructor, @AllArgsConstructor)

**Example Structure**:
```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlightStatusDTO {
    private String flightId;
    private String flightNumber;
    private String status; // scheduled, active, landed, cancelled, diverted
    private DepartureInfoDTO departure;
    private ArrivalInfoDTO arrival;
    private DelayInfoDTO delay;
    private LocalDateTime lastUpdated;
}
```

---

#### Task 3.1.2: Create Configuration Class
**File**: `backend/src/main/java/com/skyhigh/config/AviationStackConfig.java`

**Subtasks**:
- [ ] Create `@ConfigurationProperties` class for AviationStack settings
- [ ] Add fields: apiKey, baseUrl, timeout, enabled
- [ ] Create `@Bean` for RestTemplate with timeout configuration
- [ ] Create `@Bean` for WebClient (alternative HTTP client)
- [ ] Add request/response logging interceptor
- [ ] Validate configuration on startup

**Example**:
```java
@Configuration
@ConfigurationProperties(prefix = "external.aviationstack")
@Data
public class AviationStackConfig {
    private String apiKey;
    private String baseUrl;
    private Integer timeout = 5000;
    private Boolean enabled = true;
    
    @Bean
    @ConditionalOnProperty(name = "external.aviationstack.enabled", havingValue = "true")
    public RestTemplate aviationStackRestTemplate() {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.setRequestFactory(
            new SimpleClientHttpRequestFactory() {{
                setConnectTimeout(timeout);
                setReadTimeout(timeout);
            }}
        );
        return restTemplate;
    }
}
```

---

#### Task 3.1.3: Create Service Interface
**File**: `backend/src/main/java/com/skyhigh/service/FlightStatusService.java`

**Subtasks**:
- [ ] Create interface `FlightStatusService`
- [ ] Define method: `FlightStatusDTO getFlightStatus(String flightNumber)`
- [ ] Define method: `FlightStatusDTO getFlightStatusWithFallback(String flightNumber)`
- [ ] Add JavaDoc documentation

**Example**:
```java
public interface FlightStatusService {
    /**
     * Fetches real-time flight status from AviationStack API
     * @param flightNumber IATA flight number (e.g., "SK1234")
     * @return Flight status with gate, delay, and timing information
     * @throws FlightNotFoundException if flight not found
     * @throws ExternalServiceException if API call fails
     */
    FlightStatusDTO getFlightStatus(String flightNumber);
    
    /**
     * Fetches flight status with automatic fallback to local database
     * @param flightNumber IATA flight number
     * @return Flight status from API or local database
     */
    FlightStatusDTO getFlightStatusWithFallback(String flightNumber);
}
```

---

#### Task 3.1.4: Implement Service with Caching
**File**: `backend/src/main/java/com/skyhigh/service/impl/FlightStatusServiceImpl.java`

**Subtasks**:
- [ ] Create `@Service` implementation class
- [ ] Inject `RestTemplate` and `AviationStackConfig`
- [ ] Inject `FlightRepository` for fallback
- [ ] Implement `getFlightStatus()` with API call
- [ ] Add `@Cacheable` annotation (cache key: flightNumber)
- [ ] Implement response mapping from AviationStack to DTO
- [ ] Implement `getFlightStatusWithFallback()` with try-catch
- [ ] Add comprehensive logging (INFO, WARN, ERROR)
- [ ] Handle null/empty responses
- [ ] Parse flight status enum correctly

**Example**:
```java
@Service
@Slf4j
public class FlightStatusServiceImpl implements FlightStatusService {
    
    private final RestTemplate restTemplate;
    private final AviationStackConfig config;
    private final FlightRepository flightRepository;
    
    @Override
    @Cacheable(value = "flightStatus", key = "#flightNumber", unless = "#result == null")
    public FlightStatusDTO getFlightStatus(String flightNumber) {
        log.info("Fetching flight status for: {}", flightNumber);
        
        String url = String.format("%s/flights?flight_iata=%s&access_key=%s",
            config.getBaseUrl(), flightNumber, config.getApiKey());
        
        try {
            AviationStackResponse response = restTemplate.getForObject(url, AviationStackResponse.class);
            
            if (response == null || response.getData().isEmpty()) {
                throw new FlightNotFoundException("Flight not found: " + flightNumber);
            }
            
            return mapToFlightStatusDTO(response.getData().get(0));
            
        } catch (RestClientException e) {
            log.error("Failed to fetch flight status from AviationStack", e);
            throw new ExternalServiceException("AviationStack API unavailable", e);
        }
    }
    
    @Override
    public FlightStatusDTO getFlightStatusWithFallback(String flightNumber) {
        try {
            return getFlightStatus(flightNumber);
        } catch (Exception e) {
            log.warn("AviationStack API failed, using fallback: {}", e.getMessage());
            return getFallbackFlightStatus(flightNumber);
        }
    }
    
    private FlightStatusDTO getFallbackFlightStatus(String flightNumber) {
        return flightRepository.findByFlightNumber(flightNumber)
            .map(this::mapFlightEntityToDTO)
            .orElseThrow(() -> new FlightNotFoundException(flightNumber));
    }
}
```

---

#### Task 3.1.5: Add Circuit Breaker
**File**: Same as 3.1.4

**Subtasks**:
- [ ] Add `@CircuitBreaker` annotation to service method
- [ ] Configure fallback method
- [ ] Add `@Retry` annotation with exponential backoff
- [ ] Test circuit breaker behavior (open/closed/half-open states)
- [ ] Add metrics for circuit breaker state

**Example**:
```java
@CircuitBreaker(name = "aviationstack", fallbackMethod = "getFallbackFlightStatus")
@Retry(name = "aviationstack")
@Cacheable(value = "flightStatus", key = "#flightNumber")
public FlightStatusDTO getFlightStatus(String flightNumber) {
    // Implementation
}
```

---

#### Task 3.1.6: Create REST Controller Endpoint
**File**: `backend/src/main/java/com/skyhigh/controller/FlightController.java`

**Subtasks**:
- [ ] Create or update `FlightController`
- [ ] Add endpoint: `GET /api/v1/flights/{flightId}/status`
- [ ] Inject `FlightStatusService`
- [ ] Add request validation
- [ ] Add error handling (@ExceptionHandler)
- [ ] Add Swagger/OpenAPI annotations
- [ ] Add rate limiting (20 requests per 10 seconds)
- [ ] Return standardized response format

**Example**:
```java
@RestController
@RequestMapping("/api/v1/flights")
@Slf4j
public class FlightController {
    
    private final FlightStatusService flightStatusService;
    
    @GetMapping("/{flightNumber}/status")
    @Operation(summary = "Get real-time flight status")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Flight status retrieved"),
        @ApiResponse(responseCode = "404", description = "Flight not found"),
        @ApiResponse(responseCode = "503", description = "Service unavailable")
    })
    public ResponseEntity<FlightStatusDTO> getFlightStatus(
            @PathVariable @Pattern(regexp = "^[A-Z]{2}\\d{3,4}$") String flightNumber) {
        
        log.info("Flight status request for: {}", flightNumber);
        FlightStatusDTO status = flightStatusService.getFlightStatusWithFallback(flightNumber);
        return ResponseEntity.ok(status);
    }
}
```

---

#### Task 3.1.7: Update Check-In Flow
**File**: `backend/src/main/java/com/skyhigh/service/impl/CheckInServiceImpl.java`

**Subtasks**:
- [ ] Inject `FlightStatusService` into `CheckInServiceImpl`
- [ ] Call `getFlightStatusWithFallback()` at check-in initiation
- [ ] Validate flight status before allowing check-in
- [ ] Block check-in if flight status is "cancelled"
- [ ] Display warning if flight status is "delayed" (>3 hours)
- [ ] Include gate information in check-in response
- [ ] Update boarding pass generation with latest gate info
- [ ] Add flight status to check-in audit logs

**Validation Logic**:
```java
@Transactional
public CheckInDTO initiateCheckIn(String passengerId, String flightId) {
    // Fetch real-time flight status
    FlightStatusDTO flightStatus = flightStatusService.getFlightStatusWithFallback(flightId);
    
    // Validate flight status
    if ("cancelled".equalsIgnoreCase(flightStatus.getStatus())) {
        throw new CheckInNotAllowedException("Flight has been cancelled");
    }
    
    if ("diverted".equalsIgnoreCase(flightStatus.getStatus())) {
        throw new CheckInNotAllowedException("Flight has been diverted");
    }
    
    // Continue with check-in process...
}
```

---

#### Task 3.1.8: Create Custom Exceptions
**File**: `backend/src/main/java/com/skyhigh/exception/`

**Subtasks**:
- [ ] Create `ExternalServiceException.java`
- [ ] Create `FlightNotFoundException.java` (if not exists)
- [ ] Create `CheckInNotAllowedException.java` (if not exists)
- [ ] Add exception handlers in `GlobalExceptionHandler.java`
- [ ] Return appropriate HTTP status codes (404, 503)

---

#### Task 3.1.9: Add Monitoring & Metrics
**File**: `backend/src/main/java/com/skyhigh/service/impl/FlightStatusServiceImpl.java`

**Subtasks**:
- [ ] Add custom metrics for API calls (success/failure)
- [ ] Track API response time
- [ ] Count cache hits vs misses
- [ ] Monitor circuit breaker state changes
- [ ] Log all API calls with duration
- [ ] Create CloudWatch dashboard for AviationStack metrics

**Metrics to Track**:
- `aviationstack.api.calls.total` (counter)
- `aviationstack.api.calls.success` (counter)
- `aviationstack.api.calls.failure` (counter)
- `aviationstack.api.response.time` (timer)
- `aviationstack.cache.hits` (counter)
- `aviationstack.cache.misses` (counter)
- `aviationstack.circuit.breaker.state` (gauge)

---

### 3.2 Frontend Implementation

#### Task 3.2.1: Create Flight Status Component
**File**: `frontend/src/components/FlightStatus.tsx`

**Subtasks**:
- [ ] Create React component to display flight status
- [ ] Show flight number, status badge, gate, terminal
- [ ] Display delay information with warning icon
- [ ] Highlight gate changes prominently
- [ ] Add loading state while fetching
- [ ] Add error state if fetch fails
- [ ] Style with Material-UI or Tailwind CSS
- [ ] Make responsive for mobile devices

**Example Component**:
```tsx
interface FlightStatusProps {
  flightNumber: string;
}

export const FlightStatus: React.FC<FlightStatusProps> = ({ flightNumber }) => {
  const { data, loading, error } = useFlightStatus(flightNumber);
  
  if (loading) return <Skeleton />;
  if (error) return <Alert severity="warning">Unable to fetch flight status</Alert>;
  
  return (
    <Card>
      <CardContent>
        <Typography variant="h6">{flightNumber}</Typography>
        <StatusBadge status={data.status} />
        
        {data.status === 'cancelled' && (
          <Alert severity="error">
            This flight has been cancelled. Please contact customer service.
          </Alert>
        )}
        
        {data.delay && data.delay.duration > 0 && (
          <Alert severity="warning">
            Flight delayed by {data.delay.duration} minutes. {data.delay.reason}
          </Alert>
        )}
        
        <Grid container spacing={2}>
          <Grid item xs={6}>
            <Typography variant="caption">Departure</Typography>
            <Typography>{data.departure.airport} ({data.departure.iata})</Typography>
            <Typography>Gate: {data.departure.gate}</Typography>
            <Typography>Terminal: {data.departure.terminal}</Typography>
          </Grid>
          <Grid item xs={6}>
            <Typography variant="caption">Arrival</Typography>
            <Typography>{data.arrival.airport} ({data.arrival.iata})</Typography>
            <Typography>Gate: {data.arrival.gate}</Typography>
            <Typography>Terminal: {data.arrival.terminal}</Typography>
          </Grid>
        </Grid>
      </CardContent>
    </Card>
  );
};
```

---

#### Task 3.2.2: Create API Service Hook
**File**: `frontend/src/hooks/useFlightStatus.ts`

**Subtasks**:
- [ ] Create custom React hook `useFlightStatus`
- [ ] Use Axios to call backend API
- [ ] Handle loading, error, and success states
- [ ] Implement automatic refresh every 5 minutes
- [ ] Add error handling with retry logic
- [ ] Cache response in React state

**Example**:
```typescript
export const useFlightStatus = (flightNumber: string) => {
  const [data, setData] = useState<FlightStatusDTO | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  
  useEffect(() => {
    const fetchStatus = async () => {
      try {
        const response = await axios.get(`/api/v1/flights/${flightNumber}/status`);
        setData(response.data);
        setError(null);
      } catch (err) {
        setError('Failed to fetch flight status');
        console.error(err);
      } finally {
        setLoading(false);
      }
    };
    
    fetchStatus();
    const interval = setInterval(fetchStatus, 5 * 60 * 1000); // Refresh every 5 min
    
    return () => clearInterval(interval);
  }, [flightNumber]);
  
  return { data, loading, error };
};
```

---

#### Task 3.2.3: Integrate into Check-In Flow
**File**: `frontend/src/pages/CheckInPage.tsx`

**Subtasks**:
- [ ] Import `FlightStatus` component
- [ ] Display flight status at top of check-in page
- [ ] Block check-in UI if flight is cancelled
- [ ] Show warning banner if flight is delayed
- [ ] Update boarding pass display with gate information
- [ ] Add refresh button for manual status update
- [ ] Handle loading and error states gracefully

---

### 3.3 Database Updates

#### Task 3.3.1: Add Flight Status Cache Table (Optional)
**File**: `backend/src/main/resources/db/migration/V9__create_flight_status_cache.sql`

**Subtasks**:
- [ ] Create `flight_status_cache` table
- [ ] Add columns: flight_number, status, gate, terminal, cached_at
- [ ] Add index on flight_number
- [ ] Add TTL column (expires_at)
- [ ] Create cleanup scheduled task for expired cache entries

**SQL**:
```sql
CREATE TABLE flight_status_cache (
  id BIGSERIAL PRIMARY KEY,
  flight_number VARCHAR(10) NOT NULL,
  status VARCHAR(20) NOT NULL,
  departure_gate VARCHAR(10),
  departure_terminal VARCHAR(10),
  arrival_gate VARCHAR(10),
  arrival_terminal VARCHAR(10),
  delay_minutes INT DEFAULT 0,
  cached_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  expires_at TIMESTAMP NOT NULL,
  raw_response JSONB,
  INDEX idx_flight_number (flight_number),
  INDEX idx_expires_at (expires_at)
);
```

---

### 3.4 Configuration & Environment

#### Task 3.4.1: Environment Variables
**File**: `backend/.env` and deployment configs

**Subtasks**:
- [ ] Add `AVIATIONSTACK_API_KEY` to `.env.example`
- [ ] Add to `.gitignore` to prevent committing real keys
- [ ] Document in README.md how to obtain API key
- [ ] Add to Docker Compose environment variables
- [ ] Add to Terraform/CloudFormation for production
- [ ] Add to GitHub Actions secrets for CI/CD

**Example `.env`**:
```bash
# AviationStack API Configuration
AVIATIONSTACK_API_KEY=your_api_key_here
AVIATIONSTACK_ENABLED=true
```

---

#### Task 3.4.2: Update Docker Compose
**File**: `docker-compose.yml`

**Subtasks**:
- [ ] Add environment variable for AviationStack API key
- [ ] Pass through to backend container
- [ ] Document in comments

**Example**:
```yaml
services:
  backend:
    image: skyhigh/backend:latest
    environment:
      - AVIATIONSTACK_API_KEY=${AVIATIONSTACK_API_KEY}
      - SPRING_PROFILES_ACTIVE=prod
```

---

## 4. Testing Requirements

### 4.1 Unit Tests

#### Task 4.1.1: Service Layer Tests
**File**: `backend/src/test/java/com/skyhigh/service/FlightStatusServiceTest.java`

**Test Cases**:
- [ ] Test successful API call and response mapping
- [ ] Test API returns empty data (flight not found)
- [ ] Test API timeout (should throw exception)
- [ ] Test API returns 404 (should throw FlightNotFoundException)
- [ ] Test API returns 429 (rate limit exceeded)
- [ ] Test fallback to local database when API fails
- [ ] Test caching (second call should use cache)
- [ ] Test cache expiration (after 5 minutes)
- [ ] Mock RestTemplate responses using Mockito

**Example Test**:
```java
@ExtendWith(MockitoExtension.class)
class FlightStatusServiceTest {
    
    @Mock
    private RestTemplate restTemplate;
    
    @Mock
    private FlightRepository flightRepository;
    
    @InjectMocks
    private FlightStatusServiceImpl flightStatusService;
    
    @Test
    void getFlightStatus_Success() {
        // Given
        AviationStackResponse mockResponse = createMockResponse();
        when(restTemplate.getForObject(anyString(), eq(AviationStackResponse.class)))
            .thenReturn(mockResponse);
        
        // When
        FlightStatusDTO result = flightStatusService.getFlightStatus("SK1234");
        
        // Then
        assertNotNull(result);
        assertEquals("SK1234", result.getFlightNumber());
        assertEquals("scheduled", result.getStatus());
    }
    
    @Test
    void getFlightStatus_ApiFailure_UsesFallback() {
        // Given
        when(restTemplate.getForObject(anyString(), any()))
            .thenThrow(new RestClientException("API unavailable"));
        
        Flight mockFlight = createMockFlight();
        when(flightRepository.findByFlightNumber("SK1234"))
            .thenReturn(Optional.of(mockFlight));
        
        // When
        FlightStatusDTO result = flightStatusService.getFlightStatusWithFallback("SK1234");
        
        // Then
        assertNotNull(result);
        verify(flightRepository).findByFlightNumber("SK1234");
    }
}
```

---

#### Task 4.1.2: Controller Tests
**File**: `backend/src/test/java/com/skyhigh/controller/FlightControllerTest.java`

**Test Cases**:
- [ ] Test GET /flights/{flightNumber}/status returns 200
- [ ] Test invalid flight number format returns 400
- [ ] Test flight not found returns 404
- [ ] Test service unavailable returns 503
- [ ] Test rate limiting (exceed 20 requests in 10 seconds)
- [ ] Use `@WebMvcTest` for controller testing
- [ ] Mock service layer

---

### 4.2 Integration Tests

#### Task 4.2.1: End-to-End API Test
**File**: `backend/src/test/java/com/skyhigh/integration/FlightStatusIntegrationTest.java`

**Test Cases**:
- [ ] Test full flow: API call → cache → response
- [ ] Test circuit breaker opens after failures
- [ ] Test fallback to database works
- [ ] Use TestContainers for PostgreSQL
- [ ] Use WireMock to mock AviationStack API
- [ ] Verify caching behavior
- [ ] Test concurrent requests

---

### 4.3 Frontend Tests

#### Task 4.3.1: Component Tests
**File**: `frontend/src/components/FlightStatus.test.tsx`

**Test Cases**:
- [ ] Test component renders with valid data
- [ ] Test loading state displays skeleton
- [ ] Test error state displays alert
- [ ] Test cancelled flight shows error message
- [ ] Test delayed flight shows warning
- [ ] Test gate change is highlighted
- [ ] Use React Testing Library
- [ ] Mock API responses with MSW (Mock Service Worker)

---

### 4.4 Manual Testing

#### Task 4.4.1: API Testing with Postman
**Subtasks**:
- [ ] Create Postman collection for flight status endpoints
- [ ] Test with real AviationStack API key
- [ ] Test with various flight numbers (valid/invalid)
- [ ] Test rate limiting behavior
- [ ] Test caching (same request within 5 minutes)
- [ ] Test fallback when API is disabled
- [ ] Document test results

---

#### Task 4.4.2: UI Testing
**Subtasks**:
- [ ] Test flight status display on check-in page
- [ ] Test with on-time flight
- [ ] Test with delayed flight
- [ ] Test with cancelled flight
- [ ] Test gate change display
- [ ] Test on mobile devices
- [ ] Test auto-refresh after 5 minutes

---

## 5. Deployment Checklist

### 5.1 Pre-Deployment

- [ ] Code review completed
- [ ] All unit tests passing (80%+ coverage)
- [ ] Integration tests passing
- [ ] API key obtained and tested
- [ ] Configuration validated in all environments
- [ ] Documentation updated (README, API docs)
- [ ] Postman collection created and tested

### 5.2 Deployment Steps

- [ ] Add `AVIATIONSTACK_API_KEY` to production environment variables
- [ ] Update `application-prod.yml` with production config
- [ ] Build Docker image with new code
- [ ] Push image to Docker Hub
- [ ] Deploy to EC2 instance
- [ ] Verify health check passes
- [ ] Test flight status endpoint in production
- [ ] Monitor logs for errors
- [ ] Verify caching is working
- [ ] Check CloudWatch metrics

### 5.3 Post-Deployment Validation

- [ ] Test check-in flow with real flight numbers
- [ ] Verify flight status displays correctly
- [ ] Test cancelled flight blocking
- [ ] Test delayed flight warning
- [ ] Verify fallback works (disable API temporarily)
- [ ] Check API call count (stay within free tier)
- [ ] Monitor error rates in CloudWatch
- [ ] Verify circuit breaker is functioning

---

## 6. Success Criteria

### 6.1 Functional Requirements
- ✅ Flight status fetched and displayed during check-in
- ✅ Cancelled flights block check-in with clear message
- ✅ Delayed flights show warning with delay duration
- ✅ Gate and terminal information displayed accurately
- ✅ System falls back to local database if API unavailable
- ✅ Flight status cached for 5 minutes

### 6.2 Performance Requirements
- ✅ API response time < 2 seconds (P95)
- ✅ Caching reduces API calls by 80%+
- ✅ Circuit breaker prevents cascading failures
- ✅ Stays within free tier limits (100 calls/month)

### 6.3 Quality Requirements
- ✅ Unit test coverage > 80%
- ✅ Integration tests pass
- ✅ No critical linter errors
- ✅ API documented in Swagger/OpenAPI
- ✅ Code reviewed and approved

### 6.4 Operational Requirements
- ✅ Monitoring dashboard created
- ✅ Alerts configured for failures
- ✅ API key securely stored
- ✅ Logs include request/response details
- ✅ Fallback mechanism tested and working

---

## 7. Implementation Timeline

### Phase 1: Backend Core (4-5 hours)
- Task 3.1.1: Create DTOs (1 hour)
- Task 3.1.2: Create Configuration (30 min)
- Task 3.1.3: Create Service Interface (30 min)
- Task 3.1.4: Implement Service (2 hours)
- Task 3.1.5: Add Circuit Breaker (1 hour)

### Phase 2: API & Integration (2-3 hours)
- Task 3.1.6: Create REST Controller (1 hour)
- Task 3.1.7: Update Check-In Flow (1.5 hours)
- Task 3.1.8: Create Exceptions (30 min)

### Phase 3: Frontend (2-3 hours)
- Task 3.2.1: Create Flight Status Component (1.5 hours)
- Task 3.2.2: Create API Hook (1 hour)
- Task 3.2.3: Integrate into Check-In Flow (30 min)

### Phase 4: Testing & Deployment (2-3 hours)
- Task 4.1.1: Unit Tests (1 hour)
- Task 4.1.2: Controller Tests (30 min)
- Task 4.2.1: Integration Tests (1 hour)
- Task 4.3.1: Frontend Tests (30 min)
- Deployment & Validation (1 hour)

**Total Estimated Time**: 10-14 hours

---

## 8. Risk Assessment

### 8.1 Risks & Mitigation

| Risk | Impact | Probability | Mitigation |
|------|--------|-------------|------------|
| Free tier limit exceeded | High | Medium | Aggressive caching, monitor usage |
| API downtime | Medium | Low | Fallback to local database |
| Rate limiting | Medium | Medium | Cache for 5 minutes, limit calls |
| Incorrect flight data | High | Low | Validate against local database |
| API response time slow | Medium | Low | 5-second timeout, async processing |

### 8.2 Rollback Plan

If integration causes issues:
1. Set `external.aviationstack.enabled=false` in config
2. System automatically uses local database only
3. No code rollback needed (feature flag controlled)
4. Monitor for 24 hours after deployment

---

## 9. Documentation Updates

### 9.1 Files to Update
- [x] PRD.md - Added FR-009 requirement
- [x] TRD.md - Added technical implementation details
- [ ] README.md - Add AviationStack setup instructions
- [ ] API-SPECIFICATION.md - Document new endpoint
- [ ] ARCHITECTURE.md - Update integration diagram

### 9.2 README.md Updates Needed

Add section:
```markdown
### AviationStack API Setup

1. Sign up at https://aviationstack.com/
2. Get your API key from the dashboard
3. Add to `.env` file:
   ```
   AVIATIONSTACK_API_KEY=your_api_key_here
   ```
4. Free tier: 100 calls/month (sufficient for MVP)
5. System caches responses for 5 minutes
6. Falls back to local database if API unavailable
```

---

## 10. Monitoring & Alerts

### 10.1 CloudWatch Metrics

**Custom Metrics to Create**:
- `AviationStack/APICallsTotal`
- `AviationStack/APICallsSuccess`
- `AviationStack/APICallsFailure`
- `AviationStack/ResponseTime`
- `AviationStack/CacheHitRate`
- `AviationStack/CircuitBreakerState`

### 10.2 CloudWatch Alarms

**Alarms to Configure**:
1. **High Failure Rate**
   - Metric: APICallsFailure
   - Threshold: > 5 failures in 5 minutes
   - Action: SNS notification to team

2. **Circuit Breaker Open**
   - Metric: CircuitBreakerState = OPEN
   - Threshold: > 0
   - Action: SNS notification to team

3. **API Quota Warning**
   - Metric: APICallsTotal
   - Threshold: > 80 calls/month
   - Action: Email warning about approaching limit

---

## 11. Future Enhancements

### 11.1 Post-MVP Improvements
1. **Upgrade to Paid Tier**: 500 calls/month for $9.99
2. **Real-Time Updates**: WebSocket for live gate changes
3. **Historical Data**: Track flight punctuality trends
4. **Multi-Flight Support**: Batch API calls for connecting flights
5. **Airport Weather**: Integrate with OpenWeather API
6. **Flight Tracking**: Show live aircraft position on map

### 11.2 Alternative APIs (If AviationStack Insufficient)
- **FlightAware API**: More comprehensive, higher cost
- **FlightStats API**: Enterprise-grade, requires contract
- **AeroDataBox API**: RapidAPI marketplace option

---

## 12. References

### 12.1 Documentation Links
- **AviationStack API Docs**: https://aviationstack.com/documentation
- **AviationStack Pricing**: https://aviationstack.com/product
- **Resilience4j Docs**: https://resilience4j.readme.io/docs
- **Spring Boot Caching**: https://spring.io/guides/gs/caching/
- **Spring RestTemplate**: https://docs.spring.io/spring-framework/reference/integration/rest-clients.html

### 12.2 Sample API Responses
See PRD.md Section 7.3.9 for detailed API response examples

---

## 13. Acceptance Sign-Off

### 13.1 Stakeholder Approval

| Stakeholder | Role | Approval | Date |
|-------------|------|----------|------|
| Product Owner | Requirements validation | ☐ | |
| Tech Lead | Technical approach | ☐ | |
| QA Lead | Test coverage | ☐ | |
| DevOps | Deployment readiness | ☐ | |

### 13.2 Definition of Done

- [ ] All implementation tasks completed
- [ ] All tests passing (unit + integration)
- [ ] Code reviewed and approved
- [ ] Documentation updated
- [ ] Deployed to production
- [ ] Post-deployment validation completed
- [ ] Monitoring and alerts configured
- [ ] Stakeholders signed off

---

**Task Status**: Ready for Implementation  
**Next Action**: Obtain AviationStack API key and begin Task 3.1.1  
**Contact**: skyhigh-core-team@skyhigh.com

---

*End of AviationStack Integration Task Document*
