package com.skyhigh.service;

import com.skyhigh.dto.WaitlistPositionDTO;
import com.skyhigh.dto.WaitlistResponseDTO;
import com.skyhigh.entity.Passenger;
import com.skyhigh.entity.Seat;
import com.skyhigh.entity.Waitlist;
import com.skyhigh.enums.SeatState;
import com.skyhigh.enums.WaitlistStatus;
import com.skyhigh.exception.SeatNotFoundException;
import com.skyhigh.exception.WaitlistAlreadyExistsException;
import com.skyhigh.exception.WaitlistNotFoundException;
import com.skyhigh.repository.PassengerRepository;
import com.skyhigh.repository.SeatRepository;
import com.skyhigh.repository.WaitlistRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.context.ApplicationEventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WaitlistServiceTest {

    @Mock
    private WaitlistRepository waitlistRepository;

    @Mock
    private SeatRepository seatRepository;

    @Mock
    private PassengerRepository passengerRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private WaitlistServiceImpl waitlistService;

    private Seat testSeat;
    private Waitlist testWaitlist;
    private Passenger testPassenger;

    @BeforeEach
    void setUp() {
        testSeat = new Seat();
        testSeat.setSeatId(1L);
        testSeat.setFlightId("SK1234");
        testSeat.setSeatNumber("12A");
        testSeat.setState(SeatState.CONFIRMED);

        testWaitlist = new Waitlist();
        testWaitlist.setWaitlistId(1L);
        testWaitlist.setPassengerId("P123456");
        testWaitlist.setFlightId("SK1234");
        testWaitlist.setSeatNumber("12A");
        testWaitlist.setPosition(1);
        testWaitlist.setStatus(WaitlistStatus.WAITING);
        testWaitlist.setJoinedAt(LocalDateTime.now());

        testPassenger = new Passenger();
        testPassenger.setPassengerId("P123456");
        testPassenger.setEmail("test@example.com");
    }

    @Test
    void joinWaitlist_WhenSeatNotAvailable_ShouldCreateWaitlistEntry() {
        when(seatRepository.findByFlightIdAndSeatNumber("SK1234", "12A"))
            .thenReturn(Optional.of(testSeat));
        when(waitlistRepository.existsByPassengerIdAndFlightIdAndSeatNumber(
            "P123456", "SK1234", "12A")).thenReturn(false);
        when(waitlistRepository.findMaxPositionBySeat("SK1234", "12A"))
            .thenReturn(Optional.of(0));
        when(waitlistRepository.save(any(Waitlist.class))).thenReturn(testWaitlist);

        WaitlistResponseDTO result = waitlistService.joinWaitlist("P123456", "SK1234", "12A");

        assertNotNull(result);
        assertEquals(1L, result.getWaitlistId());
        assertEquals("P123456", result.getPassengerId());
        assertEquals("SK1234", result.getFlightId());
        assertEquals("12A", result.getSeatNumber());
        assertEquals(1, result.getPosition());
        assertEquals(WaitlistStatus.WAITING, result.getStatus());

        verify(waitlistRepository).save(any(Waitlist.class));
        verify(auditLogService).logStateChange(eq("Waitlist"), anyString(), 
            isNull(), eq(WaitlistStatus.WAITING.toString()), eq("P123456"));
    }

    @Test
    void joinWaitlist_WhenSeatAvailable_ShouldThrowException() {
        testSeat.setState(SeatState.AVAILABLE);
        when(seatRepository.findByFlightIdAndSeatNumber("SK1234", "12A"))
            .thenReturn(Optional.of(testSeat));

        assertThrows(IllegalStateException.class, () ->
            waitlistService.joinWaitlist("P123456", "SK1234", "12A"));

        verify(waitlistRepository, never()).save(any(Waitlist.class));
    }

    @Test
    void joinWaitlist_WhenSeatNotFound_ShouldThrowException() {
        when(seatRepository.findByFlightIdAndSeatNumber("SK1234", "12A"))
            .thenReturn(Optional.empty());

        assertThrows(SeatNotFoundException.class, () ->
            waitlistService.joinWaitlist("P123456", "SK1234", "12A"));

        verify(waitlistRepository, never()).save(any(Waitlist.class));
    }

    @Test
    void joinWaitlist_WhenAlreadyOnWaitlist_ShouldThrowException() {
        when(seatRepository.findByFlightIdAndSeatNumber("SK1234", "12A"))
            .thenReturn(Optional.of(testSeat));
        when(waitlistRepository.existsByPassengerIdAndFlightIdAndSeatNumber(
            "P123456", "SK1234", "12A")).thenReturn(true);

        assertThrows(WaitlistAlreadyExistsException.class, () ->
            waitlistService.joinWaitlist("P123456", "SK1234", "12A"));

        verify(waitlistRepository, never()).save(any(Waitlist.class));
    }

    @Test
    void leaveWaitlist_WhenWaitingStatus_ShouldCancelEntry() {
        when(waitlistRepository.findById(1L)).thenReturn(Optional.of(testWaitlist));
        when(waitlistRepository.save(any(Waitlist.class))).thenReturn(testWaitlist);

        waitlistService.leaveWaitlist(1L);

        verify(waitlistRepository).save(argThat(w ->
            w.getStatus() == WaitlistStatus.CANCELLED));
        verify(auditLogService).logStateChange(eq("Waitlist"), eq("1"),
            eq(WaitlistStatus.WAITING.toString()),
            eq(WaitlistStatus.CANCELLED.toString()), eq("P123456"));
    }

    @Test
    void leaveWaitlist_WhenNotFound_ShouldThrowException() {
        when(waitlistRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(WaitlistNotFoundException.class, () ->
            waitlistService.leaveWaitlist(1L));

        verify(waitlistRepository, never()).save(any(Waitlist.class));
    }

    @Test
    void leaveWaitlist_WhenNotWaitingStatus_ShouldThrowException() {
        testWaitlist.setStatus(WaitlistStatus.ASSIGNED);
        when(waitlistRepository.findById(1L)).thenReturn(Optional.of(testWaitlist));

        assertThrows(IllegalStateException.class, () ->
            waitlistService.leaveWaitlist(1L));

        verify(waitlistRepository, never()).save(any(Waitlist.class));
    }

    @Test
    void processWaitlist_WhenWaitingPassengerExists_ShouldAssignSeat() {
        testSeat.setState(SeatState.AVAILABLE);
        when(waitlistRepository.findNextWaitingEntry("SK1234", "12A"))
            .thenReturn(Optional.of(testWaitlist));
        when(seatRepository.findByFlightIdAndSeatNumberWithLock("SK1234", "12A"))
            .thenReturn(Optional.of(testSeat));
        when(seatRepository.save(any(Seat.class))).thenReturn(testSeat);
        when(waitlistRepository.save(any(Waitlist.class))).thenReturn(testWaitlist);
        when(passengerRepository.findById("P123456")).thenReturn(Optional.of(testPassenger));

        waitlistService.processWaitlist("SK1234", "12A");

        verify(seatRepository).save(argThat(s ->
            s.getState() == SeatState.HELD &&
            s.getHeldBy().equals("P123456") &&
            s.getHeldUntil() != null));
        verify(waitlistRepository).save(argThat(w ->
            w.getStatus() == WaitlistStatus.ASSIGNED &&
            w.getAssignedAt() != null));
        verify(notificationService).sendSeatAssignmentNotification(
            eq("test@example.com"), eq("P123456"), eq("SK1234"), eq("12A"));
    }

    @Test
    void processWaitlist_WhenNoWaitingPassengers_ShouldDoNothing() {
        when(waitlistRepository.findNextWaitingEntry("SK1234", "12A"))
            .thenReturn(Optional.empty());

        waitlistService.processWaitlist("SK1234", "12A");

        verify(seatRepository, never()).save(any(Seat.class));
        verify(waitlistRepository, never()).save(any(Waitlist.class));
        verify(notificationService, never()).sendSeatAssignmentNotification(
            anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void processWaitlist_WhenSeatNotAvailable_ShouldNotAssign() {
        testSeat.setState(SeatState.HELD);
        when(waitlistRepository.findNextWaitingEntry("SK1234", "12A"))
            .thenReturn(Optional.of(testWaitlist));
        when(seatRepository.findByFlightIdAndSeatNumberWithLock("SK1234", "12A"))
            .thenReturn(Optional.of(testSeat));

        waitlistService.processWaitlist("SK1234", "12A");

        verify(seatRepository, never()).save(any(Seat.class));
        verify(waitlistRepository, never()).save(any(Waitlist.class));
    }

    @Test
    void getWaitlistPosition_WhenExists_ShouldReturnPosition() {
        when(waitlistRepository.findById(1L)).thenReturn(Optional.of(testWaitlist));
        when(waitlistRepository.countWaitingEntriesBySeat("SK1234", "12A"))
            .thenReturn(5L);

        WaitlistPositionDTO result = waitlistService.getWaitlistPosition(1L);

        assertNotNull(result);
        assertEquals(1L, result.getWaitlistId());
        assertEquals("SK1234", result.getFlightId());
        assertEquals("12A", result.getSeatNumber());
        assertEquals(1, result.getPosition());
        assertEquals(5L, result.getTotalWaiting());
        assertEquals(WaitlistStatus.WAITING, result.getStatus());
    }

    @Test
    void getWaitlistPosition_WhenNotFound_ShouldThrowException() {
        when(waitlistRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(WaitlistNotFoundException.class, () ->
            waitlistService.getWaitlistPosition(1L));
    }

    @Test
    void getPassengerWaitlist_ShouldReturnAllEntries() {
        Waitlist waitlist2 = new Waitlist();
        waitlist2.setWaitlistId(2L);
        waitlist2.setPassengerId("P123456");
        waitlist2.setFlightId("SK5678");
        waitlist2.setSeatNumber("15B");
        waitlist2.setPosition(2);
        waitlist2.setStatus(WaitlistStatus.WAITING);

        when(waitlistRepository.findByPassengerId("P123456"))
            .thenReturn(Arrays.asList(testWaitlist, waitlist2));

        List<WaitlistResponseDTO> results = waitlistService.getPassengerWaitlist("P123456");

        assertNotNull(results);
        assertEquals(2, results.size());
        assertEquals(1L, results.get(0).getWaitlistId());
        assertEquals(2L, results.get(1).getWaitlistId());
    }

    @Test
    void expireWaitlistAssignment_WhenAssigned_ShouldExpire() {
        testWaitlist.setStatus(WaitlistStatus.ASSIGNED);
        when(waitlistRepository.findById(1L)).thenReturn(Optional.of(testWaitlist));
        when(waitlistRepository.save(any(Waitlist.class))).thenReturn(testWaitlist);
        when(passengerRepository.findById("P123456")).thenReturn(Optional.of(testPassenger));

        waitlistService.expireWaitlistAssignment(1L);

        verify(waitlistRepository).save(argThat(w ->
            w.getStatus() == WaitlistStatus.EXPIRED &&
            w.getExpiredAt() != null));
        verify(notificationService).sendWaitlistExpirationNotification(
            eq("test@example.com"), eq("P123456"), eq("SK1234"), eq("12A"));
    }

    @Test
    void expireWaitlistAssignment_WhenNotAssigned_ShouldNotExpire() {
        when(waitlistRepository.findById(1L)).thenReturn(Optional.of(testWaitlist));

        waitlistService.expireWaitlistAssignment(1L);

        verify(waitlistRepository, never()).save(any(Waitlist.class));
        verify(notificationService, never()).sendWaitlistExpirationNotification(
            anyString(), anyString(), anyString(), anyString());
    }
}
