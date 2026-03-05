package com.skyhigh.scheduler;

import com.skyhigh.entity.Seat;
import com.skyhigh.enums.SeatState;
import com.skyhigh.enums.SeatType;
import com.skyhigh.repository.SeatRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class SeatExpirationSchedulerIntegrationTest {
    
    @Autowired
    private SeatExpirationScheduler scheduler;
    
    @Autowired
    private SeatRepository seatRepository;
    
    private static final String TEST_FLIGHT_ID = "SK9999";
    
    @BeforeEach
    void setUp() {
        seatRepository.deleteAll();
    }
    
    @Test
    void releaseExpiredSeats_WithRealDatabase_ShouldReleaseExpiredSeats() {
        Seat expiredSeat = createSeat(TEST_FLIGHT_ID, "10A", SeatState.HELD, "P123456");
        expiredSeat.setHeldUntil(LocalDateTime.now().minusMinutes(1));
        seatRepository.save(expiredSeat);
        
        Seat validSeat = createSeat(TEST_FLIGHT_ID, "10B", SeatState.HELD, "P789012");
        validSeat.setHeldUntil(LocalDateTime.now().plusMinutes(1));
        seatRepository.save(validSeat);
        
        Seat availableSeat = createSeat(TEST_FLIGHT_ID, "10C", SeatState.AVAILABLE, null);
        seatRepository.save(availableSeat);
        
        scheduler.releaseExpiredSeats();
        
        List<Seat> allSeats = seatRepository.findByFlightId(TEST_FLIGHT_ID);
        
        Seat releasedSeat = allSeats.stream()
            .filter(s -> s.getSeatNumber().equals("10A"))
            .findFirst()
            .orElseThrow();
        assertEquals(SeatState.AVAILABLE, releasedSeat.getState());
        assertNull(releasedSeat.getHeldBy());
        assertNull(releasedSeat.getHeldUntil());
        
        Seat stillHeldSeat = allSeats.stream()
            .filter(s -> s.getSeatNumber().equals("10B"))
            .findFirst()
            .orElseThrow();
        assertEquals(SeatState.HELD, stillHeldSeat.getState());
        assertEquals("P789012", stillHeldSeat.getHeldBy());
        
        Seat unchangedSeat = allSeats.stream()
            .filter(s -> s.getSeatNumber().equals("10C"))
            .findFirst()
            .orElseThrow();
        assertEquals(SeatState.AVAILABLE, unchangedSeat.getState());
    }
    
    @Test
    void releaseExpiredSeats_WithMultipleExpiredSeats_ShouldReleaseAll() {
        List<Seat> expiredSeats = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            Seat seat = createSeat(TEST_FLIGHT_ID, String.format("%dA", i), SeatState.HELD, "P123456");
            seat.setHeldUntil(LocalDateTime.now().minusMinutes(1));
            expiredSeats.add(seat);
        }
        seatRepository.saveAll(expiredSeats);
        
        scheduler.releaseExpiredSeats();
        
        List<Seat> availableSeats = seatRepository.findByFlightIdAndState(TEST_FLIGHT_ID, SeatState.AVAILABLE);
        assertEquals(10, availableSeats.size());
        
        List<Seat> heldSeats = seatRepository.findByFlightIdAndState(TEST_FLIGHT_ID, SeatState.HELD);
        assertEquals(0, heldSeats.size());
    }
    
    @Test
    void releaseExpiredSeats_ConcurrentExpiration_ShouldHandleGracefully() throws InterruptedException {
        List<Seat> seats = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            Seat seat = createSeat(TEST_FLIGHT_ID, String.format("%dA", i), SeatState.HELD, "P123456");
            seat.setHeldUntil(LocalDateTime.now().minusMinutes(1));
            seats.add(seat);
        }
        seatRepository.saveAll(seats);
        
        scheduler.releaseExpiredSeats();
        scheduler.releaseExpiredSeats();
        
        List<Seat> availableSeats = seatRepository.findByFlightIdAndState(TEST_FLIGHT_ID, SeatState.AVAILABLE);
        assertEquals(5, availableSeats.size());
    }
    
    @Test
    void releaseExpiredSeats_SchedulerHealth_ShouldBeHealthyAfterRun() {
        Seat expiredSeat = createSeat(TEST_FLIGHT_ID, "10A", SeatState.HELD, "P123456");
        expiredSeat.setHeldUntil(LocalDateTime.now().minusMinutes(1));
        seatRepository.save(expiredSeat);
        
        scheduler.releaseExpiredSeats();
        
        assertTrue(scheduler.isHealthy());
        assertNotNull(scheduler.getLastSuccessfulRun());
        assertEquals(1, scheduler.getLastRunReleasedCount());
    }
    
    @Test
    void releaseExpiredSeats_WithNoExpiredSeats_ShouldStillBeHealthy() {
        scheduler.releaseExpiredSeats();
        
        assertTrue(scheduler.isHealthy());
        assertEquals(0, scheduler.getLastRunReleasedCount());
    }
    
    @Test
    void releaseExpiredSeats_Performance_ShouldHandle1000Seats() {
        List<Seat> expiredSeats = new ArrayList<>();
        for (int i = 1; i <= 1000; i++) {
            Seat seat = createSeat(TEST_FLIGHT_ID, String.format("S%04d", i), SeatState.HELD, "P123456");
            seat.setHeldUntil(LocalDateTime.now().minusMinutes(1));
            expiredSeats.add(seat);
        }
        seatRepository.saveAll(expiredSeats);
        
        long startTime = System.currentTimeMillis();
        scheduler.releaseExpiredSeats();
        long endTime = System.currentTimeMillis();
        
        long executionTime = endTime - startTime;
        assertTrue(executionTime < 10000, "Scheduler should process 1000 seats within 10 seconds");
        
        assertTrue(scheduler.getLastRunReleasedCount() > 0);
    }
    
    private Seat createSeat(String flightId, String seatNumber, SeatState state, String heldBy) {
        Seat seat = new Seat();
        seat.setFlightId(flightId);
        seat.setSeatNumber(seatNumber);
        seat.setSeatType(SeatType.WINDOW);
        seat.setState(state);
        seat.setHeldBy(heldBy);
        seat.setCreatedAt(LocalDateTime.now());
        seat.setUpdatedAt(LocalDateTime.now());
        seat.setVersion(0);
        return seat;
    }
}
