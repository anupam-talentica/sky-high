package com.skyhigh.repository;

import com.skyhigh.entity.Seat;
import com.skyhigh.enums.SeatState;
import com.skyhigh.enums.SeatType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@DataJpaTest
@ActiveProfiles("test")
class SeatRepositoryTest {

    @Autowired
    private SeatRepository seatRepository;

    @Test
    void findByFlightIdAndState_WhenSeatsExist_ShouldReturnMatchingSeats() {
        String flightId = "TEST-FLIGHT-1";

        Seat availableSeat = new Seat();
        availableSeat.setFlightId(flightId);
        availableSeat.setSeatNumber("1A");
        availableSeat.setSeatType(SeatType.WINDOW);
        availableSeat.setState(SeatState.AVAILABLE);
        availableSeat.setCreatedAt(LocalDateTime.now());
        availableSeat.setUpdatedAt(LocalDateTime.now());

        Seat heldSeat = new Seat();
        heldSeat.setFlightId(flightId);
        heldSeat.setSeatNumber("1B");
        heldSeat.setSeatType(SeatType.MIDDLE);
        heldSeat.setState(SeatState.HELD);
        heldSeat.setCreatedAt(LocalDateTime.now());
        heldSeat.setUpdatedAt(LocalDateTime.now());

        seatRepository.save(availableSeat);
        seatRepository.save(heldSeat);

        List<Seat> availableSeats = seatRepository.findByFlightIdAndState(flightId, SeatState.AVAILABLE);

        assertEquals(1, availableSeats.size());
        assertEquals("1A", availableSeats.get(0).getSeatNumber());
        assertEquals(SeatState.AVAILABLE, availableSeats.get(0).getState());
    }

    @Test
    void findByFlightIdAndSeatNumber_WhenSeatExists_ShouldReturnSeat() {
        String flightId = "TEST-FLIGHT-2";

        Seat seat = new Seat();
        seat.setFlightId(flightId);
        seat.setSeatNumber("2A");
        seat.setSeatType(SeatType.AISLE);
        seat.setState(SeatState.AVAILABLE);
        seat.setCreatedAt(LocalDateTime.now());
        seat.setUpdatedAt(LocalDateTime.now());

        seat = seatRepository.save(seat);

        Optional<Seat> found = seatRepository.findByFlightIdAndSeatNumber(flightId, "2A");

        assertTrue(found.isPresent());
        assertEquals(seat.getSeatId(), found.get().getSeatId());
    }

    @Test
    void uniqueConstraint_OnFlightAndSeatNumber_ShouldPreventDuplicates() {
        String flightId = "TEST-FLIGHT-3";

        Seat first = new Seat();
        first.setFlightId(flightId);
        first.setSeatNumber("3A");
        first.setSeatType(SeatType.WINDOW);
        first.setState(SeatState.AVAILABLE);
        first.setCreatedAt(LocalDateTime.now());
        first.setUpdatedAt(LocalDateTime.now());

        Seat duplicate = new Seat();
        duplicate.setFlightId(flightId);
        duplicate.setSeatNumber("3A");
        duplicate.setSeatType(SeatType.WINDOW);
        duplicate.setState(SeatState.AVAILABLE);
        duplicate.setCreatedAt(LocalDateTime.now());
        duplicate.setUpdatedAt(LocalDateTime.now());

        seatRepository.saveAndFlush(first);

        assertThrows(Exception.class, () -> {
            seatRepository.saveAndFlush(duplicate);
        });
    }
}

