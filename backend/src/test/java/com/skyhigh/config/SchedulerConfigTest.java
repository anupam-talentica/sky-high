package com.skyhigh.config;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import static org.junit.jupiter.api.Assertions.*;

class SchedulerConfigTest {
    
    @Test
    void taskScheduler_ShouldCreateThreadPoolTaskScheduler() {
        SchedulerConfig config = new SchedulerConfig();
        
        TaskScheduler scheduler = config.taskScheduler();
        
        assertNotNull(scheduler);
        assertTrue(scheduler instanceof ThreadPoolTaskScheduler);
    }
    
    @Test
    void taskScheduler_ShouldConfigurePoolSize() {
        SchedulerConfig config = new SchedulerConfig();
        
        ThreadPoolTaskScheduler scheduler = (ThreadPoolTaskScheduler) config.taskScheduler();
        
        assertEquals(5, scheduler.getScheduledThreadPoolExecutor().getCorePoolSize());
    }
    
    @Test
    void taskScheduler_ShouldSetThreadNamePrefix() {
        SchedulerConfig config = new SchedulerConfig();
        
        ThreadPoolTaskScheduler scheduler = (ThreadPoolTaskScheduler) config.taskScheduler();
        
        assertTrue(scheduler.getThreadNamePrefix().startsWith("scheduler-"));
    }
}
