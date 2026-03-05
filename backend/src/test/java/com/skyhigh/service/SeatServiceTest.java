package com.skyhigh.service;

import com.skyhigh.dto.SeatMapResponseDTO;
import com.skyhigh.dto.SeatReservationResponseDTO;
import com.skyhigh.entity.Seat;
import com.skyhigh.enums.SeatState;
import com.skyhigh.enums.SeatType;
import com.skyhigh.exception.InvalidStateTransitionException;
import com.skyhigh.exception.SeatConflictException;
import com.skyhigh.exception.SeatNotFoundException;
import com.skyhigh.repository.SeatRepository;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SeatServiceTest {
    
    @Mock
    private SeatRepository seatRepository;
    
    @Mock
    private AuditLogService auditLogService;
    
    @Mock
    private ApplicationEventPublisher eventPublisher;
    
    @Mock
    private SeatMapCacheService seatMapCacheService;

    @Mock
    private DistributedSeatLockService distributedSeatLockService;

    @InjectMocks
    private SeatServiceImpl seatService;
    
    private Seat availableSeat;
    private Seat heldSeat;
    private Seat confirmedSeat;
    
    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(seatService, "seatHoldDuration", 120);
        lenient().when(distributedSeatLockService.tryLock(anyString(), anyString())).thenReturn(Optional.of("test-token"));
        
        availableSeat = new Seat();
        availableSeat.setSeatId(1L);
        availableSeat.setFlightId("SK1234");
        availableSeat.setSeatNumber("12A");
        availableSeat.setSeatType(SeatType.WINDOW);
        availableSeat.setState(SeatState.AVAILABLE);
        availableSeat.setVersion(0);
        
        heldSeat = new Seat();
        heldSeat.setSeatId(2L);
        heldSeat.setFlightId("SK1234");
        heldSeat.setSeatNumber("12B");
        heldSeat.setSeatType(SeatType.MIDDLE);
        heldSeat.setState(SeatState.HELD);
        heldSeat.setHeldBy("P123456");
        heldSeat.setHeldUntil(LocalDateTime.now().plusSeconds(120));
        heldSeat.setVersion(1);
        
        confirmedSeat = new Seat();
        confirmedSeat.setSeatId(3L);
        confirmedSeat.setFlightId("SK1234");
        confirmedSeat.setSeatNumber("12C");
        confirmedSeat.setSeatType(SeatType.AISLE);
        confirmedSeat.setState(SeatState.CONFIRMED);
        confirmedSeat.setConfirmedBy("P789012");
        confirmedSeat.setVersion(2);
    }
    
    @Test
    void getAvailableSeats_WhenSeatsExist_ShouldReturnSeatMap() {
        List<Seat> seats = Arrays.asList(availableSeat, heldSeat, confirmedSeat);
        when(seatMapCacheService.get("SK1234")).thenReturn(Optional.empty());
        when(seatRepository.findByFlightId("SK1234")).thenReturn(seats);
        
        SeatMapResponseDTO result = seatService.getAvailableSeats("SK1234");
        
        assertNotNull(result);
        assertEquals("SK1234", result.getFlightId());
        assertEquals(3, result.getTotalSeats());
        assertEquals(1, result.getAvailableSeats());
        assertEquals(1, result.getHeldSeats());
        assertEquals(1, result.getConfirmedSeats());
        assertEquals(3, result.getSeats().size());
        
        verify(seatMapCacheService).get("SK1234");
        verify(seatRepository, times(1)).findByFlightId("SK1234");
        verify(seatMapCacheService).put(eq("SK1234"), any(SeatMapResponseDTO.class));
    }
    
    @Test
    void getAvailableSeats_WhenCacheHit_ShouldReturnCachedSeatMap() {
        SeatMapResponseDTO cached = SeatMapResponseDTO.builder()
            .flightId("SK1234")
            .totalSeats(3)
            .availableSeats(1)
            .heldSeats(1)
            .confirmedSeats(1)
            .seats(List.of())
            .build();
        when(seatMapCacheService.get("SK1234")).thenReturn(Optional.of(cached));
        
        SeatMapResponseDTO result = seatService.getAvailableSeats("SK1234");
        
        assertSame(cached, result);
        verify(seatRepository, never()).findByFlightId(anyString());
        verify(seatMapCacheService, never()).put(anyString(), any());
    }
    
    @Test
    void getAvailableSeats_WhenNoSeatsExist_ShouldThrowException() {
        when(seatMapCacheService.get("SK9999")).thenReturn(Optional.empty());
        when(seatRepository.findByFlightId("SK9999")).thenReturn(Arrays.asList());
        
        assertThrows(SeatNotFoundException.class, () -> {
            seatService.getAvailableSeats("SK9999");
        });
        
        verify(seatRepository, times(1)).findByFlightId("SK9999");
    }

    @Test
    void getSeatById_WhenSeatExists_ShouldReturnSeat() {
        when(seatRepository.findById(1L)).thenReturn(Optional.of(availableSeat));

        Seat result = seatService.getSeatById(1L);

        assertSame(availableSeat, result);
        verify(seatRepository).findById(1L);
    }

    @Test
    void getSeatById_WhenSeatMissing_ShouldThrowException() {
        when(seatRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(SeatNotFoundException.class, () -> seatService.getSeatById(99L));

        verify(seatRepository).findById(99L);
    }

    @Test
    void getSeatByFlightAndNumber_WhenSeatExists_ShouldReturnSeat() {
        when(seatRepository.findByFlightIdAndSeatNumber("SK1234", "12A"))
            .thenReturn(Optional.of(availableSeat));

        Seat result = seatService.getSeatByFlightAndNumber("SK1234", "12A");

        assertSame(availableSeat, result);
        verify(seatRepository).findByFlightIdAndSeatNumber("SK1234", "12A");
    }

    @Test
    void getSeatByFlightAndNumber_WhenSeatMissing_ShouldThrowException() {
        when(seatRepository.findByFlightIdAndSeatNumber("SK1234", "99Z"))
            .thenReturn(Optional.empty());

        assertThrows(SeatNotFoundException.class, () ->
            seatService.getSeatByFlightAndNumber("SK1234", "99Z"));

        verify(seatRepository).findByFlightIdAndSeatNumber("SK1234", "99Z");
    }
    
    @Test
    void reserveSeat_WhenSeatAvailable_ShouldReserveSeat() {
        when(seatRepository.findByFlightIdAndSeatNumberWithLock("SK1234", "12A"))
            .thenReturn(Optional.of(availableSeat));
        when(seatRepository.save(any(Seat.class))).thenReturn(availableSeat);
        
        SeatReservationResponseDTO result = seatService.reserveSeat("SK1234", "12A", "P123456");
        
        assertNotNull(result);
        assertEquals(1L, result.getSeatId());
        assertEquals("SK1234", result.getFlightId());
        assertEquals("12A", result.getSeatNumber());
        assertEquals(SeatState.HELD, result.getState());
        assertEquals("P123456", result.getHeldBy());
        assertNotNull(result.getHeldUntil());
        assertEquals(120, result.getHoldDurationSeconds());
        
        verify(seatRepository, times(1)).findByFlightIdAndSeatNumberWithLock("SK1234", "12A");
        verify(seatRepository, times(1)).save(any(Seat.class));
        verify(distributedSeatLockService).tryLock("SK1234", "12A");
        verify(distributedSeatLockService).unlock("SK1234", "12A", "test-token");
        verify(auditLogService, times(1)).logStateChange(
            eq("Seat"), 
            eq("1"), 
            eq("AVAILABLE"), 
            eq("HELD"), 
            eq("P123456")
        );
    }

    @Test
    void reserveSeat_WhenLockNotAcquired_ShouldThrowSeatLockConflictException() {
        when(distributedSeatLockService.tryLock("SK1234", "12A")).thenReturn(Optional.empty());

        assertThrows(com.skyhigh.exception.SeatLockConflictException.class, () -> {
            seatService.reserveSeat("SK1234", "12A", "P123456");
        });

        verify(distributedSeatLockService).tryLock("SK1234", "12A");
        verify(distributedSeatLockService, never()).unlock(anyString(), anyString(), anyString());
        verify(seatRepository, never()).findByFlightIdAndSeatNumberWithLock(anyString(), anyString());
        verify(seatRepository, never()).save(any(Seat.class));
    }
    
    @Test
    void reserveSeat_WhenSeatNotFound_ShouldThrowException() {
        when(seatRepository.findByFlightIdAndSeatNumberWithLock("SK1234", "99Z"))
            .thenReturn(Optional.empty());
        
        assertThrows(SeatNotFoundException.class, () -> {
            seatService.reserveSeat("SK1234", "99Z", "P123456");
        });

        verify(distributedSeatLockService).unlock("SK1234", "99Z", "test-token");
        verify(seatRepository, times(1)).findByFlightIdAndSeatNumberWithLock("SK1234", "99Z");
        verify(seatRepository, never()).save(any(Seat.class));
    }

    @Test
    void reserveSeat_WhenOptimisticLockFailure_ShouldThrowConflictException() {
        when(seatRepository.findByFlightIdAndSeatNumberWithLock("SK1234", "12A"))
            .thenReturn(Optional.of(availableSeat));
        when(seatRepository.save(any(Seat.class)))
            .thenThrow(new ObjectOptimisticLockingFailureException(Seat.class, 1L));

        assertThrows(SeatConflictException.class, () ->
            seatService.reserveSeat("SK1234", "12A", "P123456"));

        verify(distributedSeatLockService).unlock("SK1234", "12A", "test-token");
        verify(seatRepository).save(any(Seat.class));
    }

    @Test
    void reserveSeat_WhenTransitionStateFails_ShouldThrowInvalidStateTransition() {
        Seat seatSpy = spy(availableSeat);
        doThrow(new IllegalStateException("bad transition"))
            .when(seatSpy).transitionState(SeatState.HELD);
        when(seatRepository.findByFlightIdAndSeatNumberWithLock("SK1234", "12A"))
            .thenReturn(Optional.of(seatSpy));

        assertThrows(InvalidStateTransitionException.class, () ->
            seatService.reserveSeat("SK1234", "12A", "P123456"));

        verify(distributedSeatLockService).unlock("SK1234", "12A", "test-token");
        verify(seatRepository, never()).save(any(Seat.class));
    }

    @Test
    void reserveSeatForCheckIn_WhenSeatAvailable_ShouldReserveWithoutExpiry() {
        when(seatRepository.findByFlightIdAndSeatNumberWithLock("SK1234", "12A"))
            .thenReturn(Optional.of(availableSeat));
        when(seatRepository.save(any(Seat.class))).thenReturn(availableSeat);

        SeatReservationResponseDTO result =
            seatService.reserveSeatForCheckIn("SK1234", "12A", "P123456");

        assertNotNull(result);
        assertEquals(SeatState.HELD, result.getState());
        assertEquals("P123456", result.getHeldBy());
        assertNull(result.getHeldUntil());
        assertEquals(0, result.getHoldDurationSeconds());

        verify(distributedSeatLockService).unlock("SK1234", "12A", "test-token");
        verify(seatRepository).save(any(Seat.class));
    }

    @Test
    void reserveSeatForCheckIn_WhenLockNotAcquired_ShouldThrowSeatLockConflictException() {
        when(distributedSeatLockService.tryLock("SK1234", "12A")).thenReturn(Optional.empty());

        assertThrows(com.skyhigh.exception.SeatLockConflictException.class, () ->
            seatService.reserveSeatForCheckIn("SK1234", "12A", "P123456"));

        verify(distributedSeatLockService, never()).unlock(anyString(), anyString(), anyString());
        verify(seatRepository, never()).findByFlightIdAndSeatNumberWithLock(anyString(), anyString());
    }

    @Test
    void reserveSeatForCheckIn_WhenSeatNotFound_ShouldThrowException() {
        when(seatRepository.findByFlightIdAndSeatNumberWithLock("SK1234", "99Z"))
            .thenReturn(Optional.empty());

        assertThrows(SeatNotFoundException.class, () ->
            seatService.reserveSeatForCheckIn("SK1234", "99Z", "P123456"));

        verify(distributedSeatLockService).unlock("SK1234", "99Z", "test-token");
    }

    @Test
    void reserveSeatForCheckIn_WhenSeatAlreadyHeld_ShouldThrowConflictException() {
        when(seatRepository.findByFlightIdAndSeatNumberWithLock("SK1234", "12B"))
            .thenReturn(Optional.of(heldSeat));

        assertThrows(SeatConflictException.class, () ->
            seatService.reserveSeatForCheckIn("SK1234", "12B", "P123456"));

        verify(distributedSeatLockService).unlock("SK1234", "12B", "test-token");
        verify(seatRepository, never()).save(any(Seat.class));
    }

    @Test
    void reserveSeatForCheckIn_WhenOptimisticLockFailure_ShouldThrowConflictException() {
        when(seatRepository.findByFlightIdAndSeatNumberWithLock("SK1234", "12A"))
            .thenReturn(Optional.of(availableSeat));
        when(seatRepository.save(any(Seat.class)))
            .thenThrow(new ObjectOptimisticLockingFailureException(Seat.class, 1L));

        assertThrows(SeatConflictException.class, () ->
            seatService.reserveSeatForCheckIn("SK1234", "12A", "P123456"));

        verify(distributedSeatLockService).unlock("SK1234", "12A", "test-token");
    }

    @Test
    void reserveSeatForCheckIn_WhenTransitionStateFails_ShouldThrowInvalidStateTransition() {
        Seat seatSpy = spy(availableSeat);
        doThrow(new IllegalStateException("bad transition"))
            .when(seatSpy).transitionState(SeatState.HELD);
        when(seatRepository.findByFlightIdAndSeatNumberWithLock("SK1234", "12A"))
            .thenReturn(Optional.of(seatSpy));

        assertThrows(InvalidStateTransitionException.class, () ->
            seatService.reserveSeatForCheckIn("SK1234", "12A", "P123456"));

        verify(distributedSeatLockService).unlock("SK1234", "12A", "test-token");
        verify(seatRepository, never()).save(any(Seat.class));
    }
    
    @Test
    void reserveSeat_WhenSeatAlreadyHeld_ShouldThrowConflictException() {
        when(seatRepository.findByFlightIdAndSeatNumberWithLock("SK1234", "12B"))
            .thenReturn(Optional.of(heldSeat));
        
        assertThrows(SeatConflictException.class, () -> {
            seatService.reserveSeat("SK1234", "12B", "P123456");
        });

        verify(distributedSeatLockService).unlock("SK1234", "12B", "test-token");
        verify(seatRepository, times(1)).findByFlightIdAndSeatNumberWithLock("SK1234", "12B");
        verify(seatRepository, never()).save(any(Seat.class));
    }
    
    @Test
    void releaseSeat_WhenSeatHeld_ShouldReleaseSeat() {
        when(seatRepository.findById(2L)).thenReturn(Optional.of(heldSeat));
        when(seatRepository.save(any(Seat.class))).thenReturn(heldSeat);
        
        Seat result = seatService.releaseSeat(2L);
        
        assertNotNull(result);
        assertEquals(SeatState.AVAILABLE, result.getState());
        assertNull(result.getHeldBy());
        assertNull(result.getHeldUntil());
        
        verify(seatRepository, times(1)).findById(2L);
        verify(seatRepository, times(1)).save(any(Seat.class));
        verify(auditLogService, times(1)).logStateChange(
            eq("Seat"), 
            eq("2"), 
            eq("HELD"), 
            eq("AVAILABLE"), 
            eq("SYSTEM")
        );
    }
    
    @Test
    void releaseSeat_WhenSeatNotHeld_ShouldThrowException() {
        when(seatRepository.findById(1L)).thenReturn(Optional.of(availableSeat));
        
        assertThrows(InvalidStateTransitionException.class, () -> {
            seatService.releaseSeat(1L);
        });
        
        verify(seatRepository, times(1)).findById(1L);
        verify(seatRepository, never()).save(any(Seat.class));
    }
    
    @Test
    void confirmSeat_WhenSeatHeldByPassenger_ShouldConfirmSeat() {
        when(seatRepository.findById(2L)).thenReturn(Optional.of(heldSeat));
        when(seatRepository.save(any(Seat.class))).thenReturn(heldSeat);
        
        Seat result = seatService.confirmSeat(2L, "P123456");
        
        assertNotNull(result);
        assertEquals(SeatState.CONFIRMED, result.getState());
        assertEquals("P123456", result.getConfirmedBy());
        assertNull(result.getHeldUntil());
        
        verify(seatRepository, times(1)).findById(2L);
        verify(seatRepository, times(1)).save(any(Seat.class));
        verify(auditLogService, times(1)).logStateChange(
            eq("Seat"), 
            eq("2"), 
            eq("HELD"), 
            eq("CONFIRMED"), 
            eq("P123456")
        );
    }
    
    @Test
    void confirmSeat_WhenSeatHeldByDifferentPassenger_ShouldThrowException() {
        when(seatRepository.findById(2L)).thenReturn(Optional.of(heldSeat));
        
        assertThrows(SeatConflictException.class, () -> {
            seatService.confirmSeat(2L, "P999999");
        });
        
        verify(seatRepository, times(1)).findById(2L);
        verify(seatRepository, never()).save(any(Seat.class));
    }

    @Test
    void confirmSeat_WhenSeatNotHeld_ShouldThrowException() {
        when(seatRepository.findById(1L)).thenReturn(Optional.of(availableSeat));

        assertThrows(InvalidStateTransitionException.class, () ->
            seatService.confirmSeat(1L, "P123456"));

        verify(seatRepository).findById(1L);
        verify(seatRepository, never()).save(any(Seat.class));
    }
    
    @Test
    void cancelSeat_WhenSeatConfirmed_ShouldCancelAndReleaseSeat() {
        when(seatRepository.findById(3L)).thenReturn(Optional.of(confirmedSeat));
        when(seatRepository.save(any(Seat.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        Seat result = seatService.cancelSeat(3L);
        
        assertNotNull(result);
        
        verify(seatRepository, times(1)).findById(3L);
        verify(seatRepository, times(2)).save(any(Seat.class));
        verify(auditLogService, times(2)).logStateChange(
            eq("Seat"), 
            eq("3"), 
            anyString(), 
            anyString(), 
            anyString()
        );
        verify(eventPublisher, times(2)).publishEvent(any());
    }

    @Test
    void cancelSeat_WhenSeatNotConfirmed_ShouldThrowException() {
        when(seatRepository.findById(2L)).thenReturn(Optional.of(heldSeat));

        assertThrows(InvalidStateTransitionException.class, () -> seatService.cancelSeat(2L));

        verify(seatRepository).findById(2L);
        verify(seatRepository, never()).save(any(Seat.class));
    }
    
    @Test
    void releaseExpiredSeats_WhenExpiredSeatsExist_ShouldReleaseAll() {
        Seat expiredSeat1 = new Seat();
        expiredSeat1.setSeatId(4L);
        expiredSeat1.setFlightId("SK1234");
        expiredSeat1.setSeatNumber("13A");
        expiredSeat1.setState(SeatState.HELD);
        expiredSeat1.setHeldBy("P111111");
        expiredSeat1.setHeldUntil(LocalDateTime.now().minusSeconds(10));
        
        Seat expiredSeat2 = new Seat();
        expiredSeat2.setSeatId(5L);
        expiredSeat2.setFlightId("SK1234");
        expiredSeat2.setSeatNumber("13B");
        expiredSeat2.setState(SeatState.HELD);
        expiredSeat2.setHeldBy("P222222");
        expiredSeat2.setHeldUntil(LocalDateTime.now().minusSeconds(20));
        
        List<Seat> expiredSeats = Arrays.asList(expiredSeat1, expiredSeat2);
        when(seatRepository.findExpiredHeldSeats(any(LocalDateTime.class))).thenReturn(expiredSeats);
        when(seatRepository.save(any(Seat.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        int result = seatService.releaseExpiredSeats();
        
        assertEquals(2, result);
        verify(seatRepository, times(1)).findExpiredHeldSeats(any(LocalDateTime.class));
        verify(seatRepository, times(2)).save(any(Seat.class));
        verify(auditLogService, times(2)).logStateChange(
            eq("Seat"), 
            anyString(), 
            eq("HELD"), 
            eq("AVAILABLE"), 
            eq("SYSTEM_EXPIRATION")
        );
    }

    @Test
    void releaseExpiredSeats_WhenErrorDuringRelease_ShouldContinueAndInvalidateCache() {
        Seat expiredSeat1 = new Seat();
        expiredSeat1.setSeatId(6L);
        expiredSeat1.setFlightId("SK1234");
        expiredSeat1.setSeatNumber("14A");
        expiredSeat1.setState(SeatState.HELD);
        expiredSeat1.setHeldBy("P333333");
        expiredSeat1.setHeldUntil(LocalDateTime.now().minusSeconds(10));

        Seat expiredSeat2 = new Seat();
        expiredSeat2.setSeatId(7L);
        expiredSeat2.setFlightId("SK5678");
        expiredSeat2.setSeatNumber("14B");
        expiredSeat2.setState(SeatState.HELD);
        expiredSeat2.setHeldBy("P444444");
        expiredSeat2.setHeldUntil(LocalDateTime.now().minusSeconds(10));

        when(seatRepository.findExpiredHeldSeats(any(LocalDateTime.class)))
            .thenReturn(Arrays.asList(expiredSeat1, expiredSeat2));
        when(seatRepository.save(any(Seat.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new RuntimeException("audit fail"))
            .when(auditLogService).logStateChange(eq("Seat"), eq("6"), anyString(), anyString(), anyString());

        int result = seatService.releaseExpiredSeats();

        assertEquals(2, result);
        verify(seatRepository, times(2)).save(any(Seat.class));
        verify(eventPublisher).publishEvent(argThat(event ->
            event instanceof com.skyhigh.event.SeatMapCacheInvalidationEvent));
        verify(eventPublisher, atLeast(1)).publishEvent(argThat(event ->
            event instanceof com.skyhigh.event.SeatReleasedEvent));
    }
    
    @Test
    void releaseExpiredSeats_WhenNoExpiredSeats_ShouldReturnZero() {
        when(seatRepository.findExpiredHeldSeats(any(LocalDateTime.class))).thenReturn(Arrays.asList());
        
        int result = seatService.releaseExpiredSeats();
        
        assertEquals(0, result);
        verify(seatRepository, times(1)).findExpiredHeldSeats(any(LocalDateTime.class));
        verify(seatRepository, never()).save(any(Seat.class));
        verify(auditLogService, never()).logStateChange(anyString(), anyString(), anyString(), anyString(), anyString());
    }
}
