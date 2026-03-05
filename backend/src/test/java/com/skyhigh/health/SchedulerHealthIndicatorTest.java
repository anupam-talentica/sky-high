package com.skyhigh.health;

import com.skyhigh.scheduler.SeatExpirationScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SchedulerHealthIndicatorTest {
    
    @Mock
    private SeatExpirationScheduler seatExpirationScheduler;
    
    private SchedulerHealthIndicator healthIndicator;
    
    @BeforeEach
    void setUp() {
        healthIndicator = new SchedulerHealthIndicator(seatExpirationScheduler);
    }
    
    @Test
    void health_WhenSchedulerIsHealthy_ShouldReturnUp() {
        when(seatExpirationScheduler.isHealthy()).thenReturn(true);
        when(seatExpirationScheduler.getLastSuccessfulRun()).thenReturn(LocalDateTime.now());
        when(seatExpirationScheduler.getLastRunReleasedCount()).thenReturn(5);
        
        Health health = healthIndicator.health();
        
        assertEquals(Status.UP, health.getStatus());
        assertNotNull(health.getDetails().get("lastSuccessfulRun"));
        assertEquals(5, health.getDetails().get("lastRunReleasedCount"));
        assertEquals("healthy", health.getDetails().get("status"));
    }
    
    @Test
    void health_WhenSchedulerIsUnhealthy_ShouldReturnDown() {
        when(seatExpirationScheduler.isHealthy()).thenReturn(false);
        when(seatExpirationScheduler.getLastSuccessfulRun()).thenReturn(LocalDateTime.now().minusMinutes(10));
        when(seatExpirationScheduler.getLastRunReleasedCount()).thenReturn(0);
        
        Health health = healthIndicator.health();
        
        assertEquals(Status.DOWN, health.getStatus());
        assertEquals("unhealthy", health.getDetails().get("status"));
    }
    
    @Test
    void health_WhenSchedulerNeverRan_ShouldReturnUp() {
        when(seatExpirationScheduler.isHealthy()).thenReturn(true);
        when(seatExpirationScheduler.getLastSuccessfulRun()).thenReturn(null);
        when(seatExpirationScheduler.getLastRunReleasedCount()).thenReturn(0);
        
        Health health = healthIndicator.health();
        
        assertEquals(Status.UP, health.getStatus());
        assertEquals("never", health.getDetails().get("lastSuccessfulRun"));
    }
    
    @Test
    void health_ShouldIncludeSchedulerName() {
        when(seatExpirationScheduler.isHealthy()).thenReturn(true);
        when(seatExpirationScheduler.getLastSuccessfulRun()).thenReturn(LocalDateTime.now());
        when(seatExpirationScheduler.getLastRunReleasedCount()).thenReturn(0);
        
        Health health = healthIndicator.health();
        
        assertEquals("SeatExpirationScheduler", health.getDetails().get("scheduler"));
    }
    
    @Test
    void health_ShouldIncludeSecondsSinceLastRun() {
        LocalDateTime lastRun = LocalDateTime.now().minusSeconds(30);
        when(seatExpirationScheduler.isHealthy()).thenReturn(true);
        when(seatExpirationScheduler.getLastSuccessfulRun()).thenReturn(lastRun);
        when(seatExpirationScheduler.getLastRunReleasedCount()).thenReturn(2);
        
        Health health = healthIndicator.health();
        
        assertTrue(health.getDetails().containsKey("secondsSinceLastRun"));
        Long secondsSinceLastRun = (Long) health.getDetails().get("secondsSinceLastRun");
        assertTrue(secondsSinceLastRun >= 30 && secondsSinceLastRun <= 35);
    }
}
