package com.skyhigh.health;

import com.skyhigh.scheduler.SeatExpirationScheduler;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class SchedulerHealthIndicator implements HealthIndicator {
    
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    private final SeatExpirationScheduler seatExpirationScheduler;
    
    public SchedulerHealthIndicator(SeatExpirationScheduler seatExpirationScheduler) {
        this.seatExpirationScheduler = seatExpirationScheduler;
    }
    
    @Override
    public Health health() {
        boolean isHealthy = seatExpirationScheduler.isHealthy();
        LocalDateTime lastRun = seatExpirationScheduler.getLastSuccessfulRun();
        int lastReleasedCount = seatExpirationScheduler.getLastRunReleasedCount();
        
        Health.Builder builder = isHealthy ? Health.up() : Health.down();
        
        builder.withDetail("scheduler", "SeatExpirationScheduler")
            .withDetail("status", isHealthy ? "healthy" : "unhealthy")
            .withDetail("lastSuccessfulRun", lastRun != null ? lastRun.format(FORMATTER) : "never")
            .withDetail("lastRunReleasedCount", lastReleasedCount);
        
        if (lastRun != null) {
            long secondsSinceLastRun = java.time.Duration.between(lastRun, LocalDateTime.now()).getSeconds();
            builder.withDetail("secondsSinceLastRun", secondsSinceLastRun);
        }
        
        return builder.build();
    }
}
