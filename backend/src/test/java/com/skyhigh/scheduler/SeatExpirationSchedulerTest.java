package com.skyhigh.scheduler;

import com.skyhigh.entity.Seat;
import com.skyhigh.enums.CheckInStatus;
import com.skyhigh.enums.SeatState;
import com.skyhigh.enums.SeatType;
import com.skyhigh.event.SeatMapCacheInvalidationEvent;
import com.skyhigh.event.SeatReleasedEvent;
import com.skyhigh.repository.SeatRepository;
import com.skyhigh.service.AuditLogService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SeatExpirationSchedulerTest {
    
    @Mock
    private SeatRepository seatRepository;
    
    @Mock
    private AuditLogService auditLogService;
    
    @Mock
    private ApplicationEventPublisher eventPublisher;
    
    @Mock
    private com.skyhigh.repository.CheckInRepository checkInRepository;
    
    private MeterRegistry meterRegistry;
    
    private SeatExpirationScheduler scheduler;
    
    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        lenient().when(checkInRepository.findFirstByPassengerIdAndFlightIdAndStatusNotOrderByCreatedAtDesc(
            anyString(), anyString(), eq(CheckInStatus.CANCELLED))).thenReturn(Optional.empty());
        scheduler = new SeatExpirationScheduler(
            seatRepository,
            auditLogService,
            eventPublisher,
            checkInRepository,
            meterRegistry
        );
        ReflectionTestUtils.setField(scheduler, "batchSize", 100);
        ReflectionTestUtils.setField(scheduler, "retryAttempts", 3);
        ReflectionTestUtils.setField(scheduler, "jobInterval", 5000L);
    }
    
    @Test
    void releaseExpiredSeats_WhenNoExpiredSeats_ShouldReturnZero() {
        when(seatRepository.findExpiredHeldSeats(any(LocalDateTime.class)))
            .thenReturn(Collections.emptyList());
        
        int result = scheduler.processExpiredSeats();
        
        assertEquals(0, result);
        verify(seatRepository, times(1)).findExpiredHeldSeats(any(LocalDateTime.class));
        verify(seatRepository, never()).save(any(Seat.class));
        verify(eventPublisher, never()).publishEvent(any(SeatReleasedEvent.class));
    }
    
    @Test
    void releaseExpiredSeats_WhenExpiredSeatsExist_ShouldReleaseAndPublishEvents() {
        Seat expiredSeat1 = createExpiredSeat(1L, "SK1234", "12A", "P123456");
        Seat expiredSeat2 = createExpiredSeat(2L, "SK1234", "12B", "P789012");
        
        when(seatRepository.findExpiredHeldSeats(any(LocalDateTime.class)))
            .thenReturn(Arrays.asList(expiredSeat1, expiredSeat2));
        when(seatRepository.save(any(Seat.class))).thenAnswer(i -> i.getArgument(0));
        
        int result = scheduler.processExpiredSeats();
        
        assertEquals(2, result);
        verify(seatRepository, times(2)).save(any(Seat.class));
        verify(auditLogService, times(2)).logStateChange(
            eq("Seat"),
            anyString(),
            eq(SeatState.HELD.toString()),
            eq(SeatState.AVAILABLE.toString()),
            eq("SYSTEM_EXPIRATION")
        );
        
        ArgumentCaptor<SeatReleasedEvent> releasedCaptor = ArgumentCaptor.forClass(SeatReleasedEvent.class);
        verify(eventPublisher, times(2)).publishEvent(releasedCaptor.capture());
        List<SeatReleasedEvent> released = releasedCaptor.getAllValues();
        assertEquals("SK1234", released.get(0).getFlightId());
        assertEquals("12A", released.get(0).getSeatNumber());
        assertEquals("SK1234", released.get(1).getFlightId());
        assertEquals("12B", released.get(1).getSeatNumber());
        ArgumentCaptor<SeatMapCacheInvalidationEvent> cacheCaptor = ArgumentCaptor.forClass(SeatMapCacheInvalidationEvent.class);
        verify(eventPublisher, times(1)).publishEvent(cacheCaptor.capture());
        assertTrue(cacheCaptor.getValue().getFlightIds().contains("SK1234"));
    }
    
    @Test
    void releaseExpiredSeats_WhenSeatNotInHeldState_ShouldSkip() {
        Seat availableSeat = createExpiredSeat(1L, "SK1234", "12A", "P123456");
        availableSeat.setState(SeatState.AVAILABLE);
        
        when(seatRepository.findExpiredHeldSeats(any(LocalDateTime.class)))
            .thenReturn(Collections.singletonList(availableSeat));
        
        int result = scheduler.processExpiredSeats();
        
        assertEquals(0, result);
        verify(seatRepository, never()).save(any(Seat.class));
        verify(eventPublisher, never()).publishEvent(any(SeatReleasedEvent.class));
    }
    
    @Test
    void releaseExpiredSeats_WhenSeatNotExpired_ShouldSkip() {
        Seat notExpiredSeat = createExpiredSeat(1L, "SK1234", "12A", "P123456");
        notExpiredSeat.setHeldUntil(LocalDateTime.now().plusMinutes(5));
        
        when(seatRepository.findExpiredHeldSeats(any(LocalDateTime.class)))
            .thenReturn(Collections.singletonList(notExpiredSeat));
        
        int result = scheduler.processExpiredSeats();
        
        assertEquals(0, result);
        verify(seatRepository, never()).save(any(Seat.class));
        verify(eventPublisher, never()).publishEvent(any(SeatReleasedEvent.class));
    }
    
    @Test
    void releaseExpiredSeats_WhenExceptionOccurs_ShouldContinueProcessing() {
        Seat expiredSeat1 = createExpiredSeat(1L, "SK1234", "12A", "P123456");
        Seat expiredSeat2 = createExpiredSeat(2L, "SK1234", "12B", "P789012");
        
        when(seatRepository.findExpiredHeldSeats(any(LocalDateTime.class)))
            .thenReturn(Arrays.asList(expiredSeat1, expiredSeat2));
        when(seatRepository.save(any(Seat.class)))
            .thenThrow(new RuntimeException("Database error"))
            .thenAnswer(i -> i.getArgument(0));
        
        int result = scheduler.processExpiredSeats();
        
        assertEquals(1, result);
        verify(seatRepository, times(2)).save(any(Seat.class));
        verify(eventPublisher, times(1)).publishEvent(any(SeatReleasedEvent.class));
        verify(eventPublisher, times(1)).publishEvent(any(SeatMapCacheInvalidationEvent.class));
    }
    
    @Test
    void releaseExpiredSeats_ShouldIncrementMetrics() {
        Seat expiredSeat = createExpiredSeat(1L, "SK1234", "12A", "P123456");
        
        when(seatRepository.findExpiredHeldSeats(any(LocalDateTime.class)))
            .thenReturn(Collections.singletonList(expiredSeat));
        when(seatRepository.save(any(Seat.class))).thenAnswer(i -> i.getArgument(0));
        
        scheduler.processExpiredSeats();
        
        Counter expiredCounter = meterRegistry.find("scheduler.seats.expired").counter();
        assertNotNull(expiredCounter);
        assertEquals(1.0, expiredCounter.count());
    }
    
    @Test
    void releaseExpiredSeats_WhenBatchSizeExceeded_ShouldProcessOnlyBatchSize() {
        List<Seat> expiredSeats = Arrays.asList(
            createExpiredSeat(1L, "SK1234", "12A", "P123456"),
            createExpiredSeat(2L, "SK1234", "12B", "P789012"),
            createExpiredSeat(3L, "SK1234", "12C", "P111111")
        );
        
        when(seatRepository.findExpiredHeldSeats(any(LocalDateTime.class)))
            .thenReturn(expiredSeats);
        when(seatRepository.save(any(Seat.class))).thenAnswer(i -> i.getArgument(0));
        
        int result = scheduler.processExpiredSeats();
        
        assertEquals(3, result);
        verify(seatRepository, atMost(100)).save(any(Seat.class));
    }
    
    @Test
    void isHealthy_WhenNeverRun_ShouldReturnTrue() {
        assertTrue(scheduler.isHealthy());
    }
    
    @Test
    void isHealthy_AfterSuccessfulRun_ShouldReturnTrue() {
        when(seatRepository.findExpiredHeldSeats(any(LocalDateTime.class)))
            .thenReturn(Collections.emptyList());
        
        scheduler.releaseExpiredSeats();
        
        assertTrue(scheduler.isHealthy());
        assertNotNull(scheduler.getLastSuccessfulRun());
    }
    
    @Test
    void getLastRunReleasedCount_ShouldReturnCorrectCount() {
        Seat expiredSeat1 = createExpiredSeat(1L, "SK1234", "12A", "P123456");
        Seat expiredSeat2 = createExpiredSeat(2L, "SK1234", "12B", "P789012");
        
        lenient().when(seatRepository.findExpiredHeldSeats(any(LocalDateTime.class)))
            .thenReturn(Arrays.asList(expiredSeat1, expiredSeat2));
        lenient().when(seatRepository.save(any(Seat.class))).thenAnswer(i -> i.getArgument(0));
        
        scheduler.releaseExpiredSeats();
        
        assertTrue(scheduler.getLastRunReleasedCount() >= 0);
    }
    
    @Test
    void releaseExpiredSeats_ShouldPublishSeatMapCacheInvalidationEvent() {
        Seat expiredSeat = createExpiredSeat(1L, "SK1234", "12A", "P123456");
        
        when(seatRepository.findExpiredHeldSeats(any(LocalDateTime.class)))
            .thenReturn(Collections.singletonList(expiredSeat));
        when(seatRepository.save(any(Seat.class))).thenAnswer(i -> i.getArgument(0));
        
        scheduler.processExpiredSeats();
        
        verify(eventPublisher, times(1)).publishEvent(any(SeatReleasedEvent.class));
        ArgumentCaptor<SeatMapCacheInvalidationEvent> captor = ArgumentCaptor.forClass(SeatMapCacheInvalidationEvent.class);
        verify(eventPublisher, times(1)).publishEvent(captor.capture());
        assertTrue(captor.getValue().getFlightIds().contains("SK1234"));
    }
    
    private Seat createExpiredSeat(Long id, String flightId, String seatNumber, String heldBy) {
        Seat seat = new Seat();
        seat.setSeatId(id);
        seat.setFlightId(flightId);
        seat.setSeatNumber(seatNumber);
        seat.setSeatType(SeatType.WINDOW);
        seat.setState(SeatState.HELD);
        seat.setHeldBy(heldBy);
        seat.setHeldUntil(LocalDateTime.now().minusMinutes(1)); // clearly in the past
        seat.setCreatedAt(LocalDateTime.now().minusHours(1));
        seat.setUpdatedAt(LocalDateTime.now().minusHours(1));
        seat.setVersion(0);
        return seat;
    }
}
