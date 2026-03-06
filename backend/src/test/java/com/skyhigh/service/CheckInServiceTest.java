package com.skyhigh.service;

import com.skyhigh.dto.*;
import com.skyhigh.entity.Baggage;
import com.skyhigh.entity.CheckIn;
import com.skyhigh.entity.Reservation;
import com.skyhigh.entity.Seat;
import com.skyhigh.enums.BaggageType;
import com.skyhigh.enums.CheckInStatus;
import com.skyhigh.enums.PaymentStatus;
import com.skyhigh.enums.SeatState;
import com.skyhigh.exception.*;
import com.skyhigh.repository.CheckInRepository;
import com.skyhigh.repository.ReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CheckInServiceTest {

    @Mock
    private CheckInRepository checkInRepository;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private SeatService seatService;

    @Mock
    private BaggageService baggageService;

    @Mock
    private PaymentService paymentService;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private CheckInServiceImpl checkInService;

    private CheckInRequestDTO checkInRequest;
    private CheckIn checkIn;
    private Seat seat;
    private Baggage baggage;

    @BeforeEach
    void setUp() {
        checkInRequest = new CheckInRequestDTO();
        checkInRequest.setPassengerId("P123456");
        checkInRequest.setFlightId("SK1234");
        checkInRequest.setSeatNumber("12A");

        checkIn = new CheckIn();
        checkIn.setCheckInId("CHK-12345678");
        checkIn.setPassengerId("P123456");
        checkIn.setFlightId("SK1234");
        checkIn.setSeatId(1L);
        checkIn.setStatus(CheckInStatus.PENDING);
        checkIn.setCheckInTime(LocalDateTime.now());
        checkIn.setCreatedAt(LocalDateTime.now());
        checkIn.setUpdatedAt(LocalDateTime.now());

        seat = new Seat();
        seat.setSeatId(1L);
        seat.setFlightId("SK1234");
        seat.setSeatNumber("12A");
        seat.setState(SeatState.HELD);
        seat.setHeldBy("P123456");

        baggage = new Baggage();
        baggage.setBaggageId(1L);
        baggage.setCheckInId("CHK-12345678");
        baggage.setWeightKg(new BigDecimal("20.00"));
        baggage.setExcessWeightKg(BigDecimal.ZERO);
        baggage.setExcessFee(BigDecimal.ZERO);
        baggage.setPaymentStatus(PaymentStatus.PAID);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void startCheckIn_WhenNoExistingCheckIn_ShouldCreatePendingCheckIn() {
        when(reservationRepository.existsByPassengerIdAndFlightIdAndStatus(
                anyString(), anyString(), anyString()))
                .thenReturn(true);
        when(checkInRepository.findFirstByPassengerIdAndFlightIdAndStatusNotOrderByCreatedAtDesc(
                anyString(), anyString(), any(CheckInStatus.class)))
                .thenReturn(Optional.empty());
        when(checkInRepository.save(any(CheckIn.class)))
                .thenReturn(checkIn);

        CheckInResponseDTO response = checkInService.startCheckIn(checkInRequest);

        assertNotNull(response);
        assertEquals("P123456", response.getPassengerId());
        assertEquals("SK1234", response.getFlightId());
        assertEquals(CheckInStatus.PENDING, response.getStatus());
        assertNotNull(response.getCheckInId());

        // New behavior: no immediate seat reservation, only check-in row + audit log
        verify(seatService, never()).reserveSeat(anyString(), anyString(), anyString());
        verify(checkInRepository).save(any(CheckIn.class));
        verify(auditLogService).logStateChange(
                eq("CheckIn"),
                anyString(),
                isNull(),
                contains("PENDING"),
                eq("P123456")
        );
    }

    @Test
    void startCheckIn_WhenNoActiveReservation_ShouldThrowException() {
        when(reservationRepository.existsByPassengerIdAndFlightIdAndStatus(
                anyString(), anyString(), anyString()))
                .thenReturn(false);

        assertThrows(UnauthorizedException.class,
                () -> checkInService.startCheckIn(checkInRequest));

        verify(checkInRepository, never()).save(any(CheckIn.class));
        verify(auditLogService, never()).logStateChange(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void startCheckIn_WhenExistingNonCancelledCheckIn_ShouldReturnExisting() {
        CheckIn existingCheckIn = new CheckIn();
        existingCheckIn.setCheckInId("CHK-EXISTING");
        existingCheckIn.setPassengerId("P123456");
        existingCheckIn.setFlightId("SK1234");
        existingCheckIn.setStatus(CheckInStatus.PENDING);

        when(reservationRepository.existsByPassengerIdAndFlightIdAndStatus(
                anyString(), anyString(), anyString()))
                .thenReturn(true);
        when(checkInRepository.findFirstByPassengerIdAndFlightIdAndStatusNotOrderByCreatedAtDesc(
                anyString(), anyString(), any(CheckInStatus.class)))
                .thenReturn(Optional.of(existingCheckIn));

        CheckInResponseDTO response = checkInService.startCheckIn(checkInRequest);

        assertNotNull(response);
        assertEquals("CHK-EXISTING", response.getCheckInId());
        assertEquals(CheckInStatus.PENDING, response.getStatus());

        verify(seatService, never()).reserveSeat(anyString(), anyString(), anyString());
        verify(checkInRepository, never()).save(any(CheckIn.class));
    }

    @Test
    void startCheckIn_WhenExistingCompleted_ShouldThrowException() {
        CheckIn existingCheckIn = new CheckIn();
        existingCheckIn.setCheckInId("CHK-EXISTING");
        existingCheckIn.setPassengerId("P123456");
        existingCheckIn.setFlightId("SK1234");
        existingCheckIn.setStatus(CheckInStatus.COMPLETED);

        when(reservationRepository.existsByPassengerIdAndFlightIdAndStatus(
                anyString(), anyString(), anyString()))
                .thenReturn(true);
        when(checkInRepository.findFirstByPassengerIdAndFlightIdAndStatusNotOrderByCreatedAtDesc(
                anyString(), anyString(), any(CheckInStatus.class)))
                .thenReturn(Optional.of(existingCheckIn));

        assertThrows(InvalidCheckInStateException.class,
                () -> checkInService.startCheckIn(checkInRequest));

        verify(checkInRepository, never()).save(any(CheckIn.class));
    }

    @Test
    void selectSeat_WhenPending_ShouldReserveNewSeatAndLog() {
        checkIn.setStatus(CheckInStatus.PENDING);
        checkIn.setSeatId(99L);

        SeatReservationRequestDTO request = new SeatReservationRequestDTO();
        request.setSeatId(1L);

        SeatReservationResponseDTO reservationResponse = SeatReservationResponseDTO.builder()
                .seatId(1L)
                .seatNumber("12A")
                .state(SeatState.HELD)
                .heldUntil(LocalDateTime.now().plusMinutes(10))
                .build();

        when(checkInRepository.findById(anyString())).thenReturn(Optional.of(checkIn));
        when(seatService.getSeatById(eq(1L))).thenReturn(seat);
        when(seatService.reserveSeatForCheckIn(eq("SK1234"), eq("12A"), eq("P123456")))
                .thenReturn(reservationResponse);
        when(checkInRepository.save(any(CheckIn.class))).thenReturn(checkIn);

        CheckInResponseDTO response = checkInService.selectSeat("CHK-12345678", request);

        assertNotNull(response);
        assertEquals(1L, response.getSeatId());
        assertEquals("12A", response.getSeatNumber());

        verify(seatService).releaseSeat(eq(99L));
        verify(seatService).reserveSeatForCheckIn(eq("SK1234"), eq("12A"), eq("P123456"));
        verify(auditLogService).logStateChange(eq("CheckIn"), eq("CHK-12345678"),
                contains("seatId"), contains("seatId"), eq("P123456"));
    }

    @Test
    void selectSeat_WhenInvalidState_ShouldThrowException() {
        checkIn.setStatus(CheckInStatus.COMPLETED);
        SeatReservationRequestDTO request = new SeatReservationRequestDTO();
        request.setSeatId(1L);

        when(checkInRepository.findById(anyString())).thenReturn(Optional.of(checkIn));

        assertThrows(InvalidCheckInStateException.class,
                () -> checkInService.selectSeat("CHK-12345678", request));

        verify(seatService, never()).reserveSeat(anyString(), anyString(), anyString());
        verify(checkInRepository, never()).save(any(CheckIn.class));
    }

    @Test
    void addBaggage_WhenCheckInPending_ShouldAddBaggage() {
        BaggageDetailsDTO baggageDetails = BaggageDetailsDTO.builder()
                .weightKg(new BigDecimal("20.00"))
                .baggageType(BaggageType.CHECKED)
                .build();

        BaggageResponseDTO baggageResponse = BaggageResponseDTO.builder()
                .baggageId(1L)
                .checkInId("CHK-12345678")
                .weightKg(new BigDecimal("20.00"))
                .excessWeightKg(BigDecimal.ZERO)
                .excessFee(BigDecimal.ZERO)
                .paymentStatus(PaymentStatus.PAID)
                .build();

        when(checkInRepository.findById(anyString())).thenReturn(Optional.of(checkIn));
        when(baggageService.addBaggage(anyString(), any(BaggageDetailsDTO.class)))
                .thenReturn(baggageResponse);
        when(checkInRepository.save(any(CheckIn.class))).thenReturn(checkIn);

        BaggageResponseDTO response = checkInService.addBaggage("CHK-12345678", baggageDetails);

        assertNotNull(response);
        assertEquals(1L, response.getBaggageId());
        assertEquals(PaymentStatus.PAID, response.getPaymentStatus());
        
        verify(baggageService).addBaggage(eq("CHK-12345678"), eq(baggageDetails));
        verify(checkInRepository).save(any(CheckIn.class));
        verify(auditLogService).logStateChange(eq("CheckIn"), eq("CHK-12345678"), 
                                              anyString(), anyString(), eq("P123456"));
    }

    @Test
    void addBaggage_WhenCheckInNotPending_ShouldThrowException() {
        checkIn.setStatus(CheckInStatus.COMPLETED);

        BaggageDetailsDTO baggageDetails = BaggageDetailsDTO.builder()
                .weightKg(new BigDecimal("20.00"))
                .baggageType(BaggageType.CHECKED)
                .build();

        when(checkInRepository.findById(anyString())).thenReturn(Optional.of(checkIn));

        assertThrows(InvalidCheckInStateException.class, 
                    () -> checkInService.addBaggage("CHK-12345678", baggageDetails));
        
        verify(baggageService, never()).addBaggage(anyString(), any(BaggageDetailsDTO.class));
    }

    @Test
    void deleteBaggage_WhenLastBaggageDeleted_ShouldRevertStatusToPending() {
        checkIn.setStatus(CheckInStatus.BAGGAGE_ADDED);

        when(checkInRepository.findById(anyString())).thenReturn(Optional.of(checkIn));
        when(baggageService.getAllBaggageForCheckIn(anyString())).thenReturn(List.of());
        when(checkInRepository.save(any(CheckIn.class))).thenReturn(checkIn);

        checkInService.deleteBaggage("CHK-12345678", 1L);

        assertEquals(CheckInStatus.PENDING, checkIn.getStatus());
        verify(baggageService).deleteBaggage(eq(1L));
        verify(checkInRepository).save(any(CheckIn.class));
        verify(auditLogService).logStateChange(eq("CheckIn"), eq("CHK-12345678"),
                contains("BAGGAGE_ADDED"), contains("PENDING"), eq("P123456"));
    }

    @Test
    void processPayment_WhenNoExcessFee_ShouldSkipPayment() {
        checkIn.setStatus(CheckInStatus.BAGGAGE_ADDED);

        PaymentRequestDTO paymentRequest = new PaymentRequestDTO();
        paymentRequest.setAmount(BigDecimal.ZERO);
        paymentRequest.setPaymentMethod("CARD");

        when(checkInRepository.findById(anyString())).thenReturn(Optional.of(checkIn));
        when(baggageService.getFirstBaggageByCheckInId(anyString())).thenReturn(baggage);
        when(checkInRepository.save(any(CheckIn.class))).thenReturn(checkIn);

        PaymentResponseDTO response = checkInService.processPayment("CHK-12345678", paymentRequest);

        assertNotNull(response);
        assertEquals(PaymentStatus.PAID, response.getStatus());
        assertEquals("NO-PAYMENT-REQUIRED", response.getTransactionId());
        
        verify(paymentService, never()).processPayment(anyString(), any(PaymentRequestDTO.class));
        verify(checkInRepository).save(any(CheckIn.class));
    }

    @Test
    void processPayment_WhenExcessFeeExists_ShouldProcessPayment() {
        checkIn.setStatus(CheckInStatus.BAGGAGE_ADDED);
        baggage.setExcessFee(new BigDecimal("50.00"));
        baggage.setPaymentStatus(PaymentStatus.PENDING);

        PaymentRequestDTO paymentRequest = new PaymentRequestDTO();
        paymentRequest.setAmount(new BigDecimal("50.00"));
        paymentRequest.setPaymentMethod("CARD");

        PaymentResponseDTO paymentResponse = PaymentResponseDTO.builder()
                .transactionId("TXN-ABC123")
                .amount(new BigDecimal("50.00"))
                .status(PaymentStatus.PAID)
                .message("Payment processed successfully")
                .processedAt(LocalDateTime.now())
                .build();

        when(checkInRepository.findById(anyString())).thenReturn(Optional.of(checkIn));
        when(baggageService.getFirstBaggageByCheckInId(anyString())).thenReturn(baggage);
        when(paymentService.processPayment(anyString(), any(PaymentRequestDTO.class)))
                .thenReturn(paymentResponse);
        when(checkInRepository.save(any(CheckIn.class))).thenReturn(checkIn);

        PaymentResponseDTO response = checkInService.processPayment("CHK-12345678", paymentRequest);

        assertNotNull(response);
        assertEquals(PaymentStatus.PAID, response.getStatus());
        assertEquals("TXN-ABC123", response.getTransactionId());
        
        verify(paymentService).processPayment(eq("CHK-12345678"), eq(paymentRequest));
        verify(checkInRepository).save(any(CheckIn.class));
        verify(auditLogService).logStateChange(eq("CheckIn"), eq("CHK-12345678"), 
                                              anyString(), anyString(), eq("P123456"));
    }

    @Test
    void processPayment_WhenAmountMismatch_ShouldThrowException() {
        checkIn.setStatus(CheckInStatus.BAGGAGE_ADDED);
        baggage.setExcessFee(new BigDecimal("50.00"));

        PaymentRequestDTO paymentRequest = new PaymentRequestDTO();
        paymentRequest.setAmount(new BigDecimal("30.00"));
        paymentRequest.setPaymentMethod("CARD");

        when(checkInRepository.findById(anyString())).thenReturn(Optional.of(checkIn));
        when(baggageService.getFirstBaggageByCheckInId(anyString())).thenReturn(baggage);

        assertThrows(PaymentFailedException.class, 
                    () -> checkInService.processPayment("CHK-12345678", paymentRequest));
        
        verify(paymentService, never()).processPayment(anyString(), any(PaymentRequestDTO.class));
    }

    @Test
    void processPayment_WhenPaymentFails_ShouldThrowException() {
        checkIn.setStatus(CheckInStatus.BAGGAGE_ADDED);
        baggage.setExcessFee(new BigDecimal("50.00"));

        PaymentRequestDTO paymentRequest = new PaymentRequestDTO();
        paymentRequest.setAmount(new BigDecimal("50.00"));
        paymentRequest.setPaymentMethod("CARD");

        PaymentResponseDTO paymentResponse = PaymentResponseDTO.builder()
                .transactionId("TXN-FAILED")
                .amount(new BigDecimal("50.00"))
                .status(PaymentStatus.FAILED)
                .message("Payment failed")
                .processedAt(LocalDateTime.now())
                .build();

        when(checkInRepository.findById(anyString())).thenReturn(Optional.of(checkIn));
        when(baggageService.getFirstBaggageByCheckInId(anyString())).thenReturn(baggage);
        when(paymentService.processPayment(anyString(), any(PaymentRequestDTO.class)))
                .thenReturn(paymentResponse);

        assertThrows(PaymentFailedException.class, 
                    () -> checkInService.processPayment("CHK-12345678", paymentRequest));
        
        verify(checkInRepository, never()).save(any(CheckIn.class));
    }

    @Test
    void confirmCheckIn_WhenPaymentCompleted_ShouldConfirmCheckIn() {
        checkIn.setStatus(CheckInStatus.PAYMENT_COMPLETED);

        when(checkInRepository.findById(anyString())).thenReturn(Optional.of(checkIn));
        when(seatService.confirmSeat(anyLong(), anyString())).thenReturn(seat);
        when(checkInRepository.save(any(CheckIn.class))).thenAnswer(invocation -> {
            CheckIn savedCheckIn = invocation.getArgument(0);
            savedCheckIn.setCompletedAt(LocalDateTime.now());
            return savedCheckIn;
        });

        CheckInResponseDTO response = checkInService.confirmCheckIn("CHK-12345678");

        assertNotNull(response);
        assertEquals(CheckInStatus.COMPLETED, response.getStatus());
        assertNotNull(response.getCompletedAt());
        
        verify(seatService).confirmSeat(eq(1L), eq("P123456"));
        verify(checkInRepository).save(any(CheckIn.class));
        verify(auditLogService).logStateChange(eq("CheckIn"), eq("CHK-12345678"), 
                                              anyString(), anyString(), eq("P123456"));
    }

    @Test
    void confirmCheckIn_WhenBaggageAddedNoUnpaidFees_ShouldConfirm() {
        checkIn.setStatus(CheckInStatus.BAGGAGE_ADDED);

        BaggageResponseDTO paidBaggage = BaggageResponseDTO.builder()
                .baggageId(1L)
                .checkInId("CHK-12345678")
                .weightKg(new BigDecimal("15.00"))
                .excessWeightKg(BigDecimal.ZERO)
                .excessFee(BigDecimal.ZERO)
                .paymentStatus(PaymentStatus.PAID)
                .build();

        when(checkInRepository.findById(anyString())).thenReturn(Optional.of(checkIn));
        when(baggageService.getAllBaggageForCheckIn(anyString()))
                .thenReturn(List.of(paidBaggage));
        when(seatService.confirmSeat(anyLong(), anyString())).thenReturn(seat);
        when(checkInRepository.save(any(CheckIn.class))).thenAnswer(invocation -> {
            CheckIn savedCheckIn = invocation.getArgument(0);
            savedCheckIn.setCompletedAt(LocalDateTime.now());
            return savedCheckIn;
        });

        CheckInResponseDTO response = checkInService.confirmCheckIn("CHK-12345678");

        assertNotNull(response);
        assertEquals(CheckInStatus.COMPLETED, response.getStatus());
        verify(checkInRepository, atLeast(2)).save(any(CheckIn.class));
        verify(auditLogService).logStateChange(eq("CheckIn"), eq("CHK-12345678"),
                anyString(), contains("No payment required"), eq("P123456"));
    }

    @Test
    void confirmCheckIn_WhenBaggageAddedHasUnpaidFees_ShouldThrowException() {
        checkIn.setStatus(CheckInStatus.BAGGAGE_ADDED);

        BaggageResponseDTO unpaidBaggage = BaggageResponseDTO.builder()
                .baggageId(1L)
                .checkInId("CHK-12345678")
                .weightKg(new BigDecimal("15.00"))
                .excessWeightKg(new BigDecimal("5.00"))
                .excessFee(new BigDecimal("30.00"))
                .paymentStatus(PaymentStatus.PENDING)
                .build();

        when(checkInRepository.findById(anyString())).thenReturn(Optional.of(checkIn));
        when(baggageService.getAllBaggageForCheckIn(anyString()))
                .thenReturn(List.of(unpaidBaggage));

        assertThrows(InvalidCheckInStateException.class,
                () -> checkInService.confirmCheckIn("CHK-12345678"));

        verify(seatService, never()).confirmSeat(anyLong(), anyString());
        verify(checkInRepository, never()).save(any(CheckIn.class));
    }

    @Test
    void confirmCheckIn_WhenPendingWithNoBaggage_ShouldConfirm() {
        checkIn.setStatus(CheckInStatus.PENDING);

        when(checkInRepository.findById(anyString())).thenReturn(Optional.of(checkIn));
        when(baggageService.getAllBaggageForCheckIn(anyString())).thenReturn(List.of());
        when(seatService.confirmSeat(anyLong(), anyString())).thenReturn(seat);
        when(checkInRepository.save(any(CheckIn.class))).thenAnswer(invocation -> {
            CheckIn savedCheckIn = invocation.getArgument(0);
            savedCheckIn.setCompletedAt(LocalDateTime.now());
            return savedCheckIn;
        });

        CheckInResponseDTO response = checkInService.confirmCheckIn("CHK-12345678");

        assertNotNull(response);
        assertEquals(CheckInStatus.COMPLETED, response.getStatus());
        verify(checkInRepository, atLeast(2)).save(any(CheckIn.class));
        verify(auditLogService).logStateChange(eq("CheckIn"), eq("CHK-12345678"),
                anyString(), contains("No baggage added"), eq("P123456"));
    }

    @Test
    void cancelCheckIn_WhenCheckInPending_ShouldCancelAndReleaseSeat() {
        when(checkInRepository.findById(anyString())).thenReturn(Optional.of(checkIn));
        when(seatService.releaseSeat(anyLong())).thenReturn(seat);
        when(checkInRepository.save(any(CheckIn.class))).thenAnswer(invocation -> {
            CheckIn savedCheckIn = invocation.getArgument(0);
            savedCheckIn.setCancelledAt(LocalDateTime.now());
            return savedCheckIn;
        });

        CheckInResponseDTO response = checkInService.cancelCheckIn("CHK-12345678");

        assertNotNull(response);
        assertEquals(CheckInStatus.CANCELLED, response.getStatus());
        assertNotNull(response.getCancelledAt());
        
        verify(seatService).releaseSeat(eq(1L));
        verify(checkInRepository).save(any(CheckIn.class));
        verify(auditLogService).logStateChange(eq("CheckIn"), eq("CHK-12345678"), 
                                              anyString(), anyString(), eq("P123456"));
    }

    @Test
    void cancelCheckIn_WhenAlreadyCancelled_ShouldThrowException() {
        checkIn.setStatus(CheckInStatus.CANCELLED);

        when(checkInRepository.findById(anyString())).thenReturn(Optional.of(checkIn));

        assertThrows(InvalidCheckInStateException.class, 
                    () -> checkInService.cancelCheckIn("CHK-12345678"));
        
        verify(seatService, never()).releaseSeat(anyLong());
        verify(checkInRepository, never()).save(any(CheckIn.class));
    }

    @Test
    void cancelCheckIn_WhenCompleted_ShouldThrowException() {
        checkIn.setStatus(CheckInStatus.COMPLETED);

        when(checkInRepository.findById(anyString())).thenReturn(Optional.of(checkIn));

        assertThrows(InvalidCheckInStateException.class, 
                    () -> checkInService.cancelCheckIn("CHK-12345678"));
        
        verify(seatService, never()).releaseSeat(anyLong());
        verify(checkInRepository, never()).save(any(CheckIn.class));
    }

    @Test
    void getCheckInDetails_WhenCheckInExists_ShouldReturnDetails() {
        when(checkInRepository.findById(anyString())).thenReturn(Optional.of(checkIn));

        CheckInResponseDTO response = checkInService.getCheckInDetails("CHK-12345678");

        assertNotNull(response);
        assertEquals("CHK-12345678", response.getCheckInId());
        assertEquals("P123456", response.getPassengerId());
        assertEquals(CheckInStatus.PENDING, response.getStatus());
        
        verify(checkInRepository).findById(eq("CHK-12345678"));
    }

    @Test
    void getCheckInDetails_WhenCompleted_ShouldIncludeBoardingPassAndTotals() {
        checkIn.setStatus(CheckInStatus.COMPLETED);
        checkIn.setCompletedAt(LocalDateTime.now());

        BaggageResponseDTO baggageResponse = BaggageResponseDTO.builder()
                .baggageId(1L)
                .checkInId("CHK-12345678")
                .weightKg(new BigDecimal("20.00"))
                .excessWeightKg(new BigDecimal("2.00"))
                .excessFee(new BigDecimal("15.00"))
                .paymentStatus(PaymentStatus.PAID)
                .build();

        when(checkInRepository.findById(anyString())).thenReturn(Optional.of(checkIn));
        when(seatService.getSeatById(anyLong())).thenReturn(seat);
        when(baggageService.getAllBaggageForCheckIn(anyString()))
                .thenReturn(List.of(baggageResponse));

        CheckInResponseDTO response = checkInService.getCheckInDetails("CHK-12345678");

        assertNotNull(response);
        assertEquals(new BigDecimal("15.00"), response.getTotalBaggageFee());
        assertNotNull(response.getBoardingPass());
        assertTrue(response.getBoardingPass().contains("BOARDING PASS"));
    }

    @Test
    void getCheckInDetails_WhenCheckInNotFound_ShouldThrowException() {
        when(checkInRepository.findById(anyString())).thenReturn(Optional.empty());

        assertThrows(CheckInNotFoundException.class, 
                    () -> checkInService.getCheckInDetails("CHK-INVALID"));
        
        verify(checkInRepository).findById(eq("CHK-INVALID"));
    }

    @Test
    void getCheckInById_WhenCheckInExists_ShouldReturnCheckIn() {
        when(checkInRepository.findById(anyString())).thenReturn(Optional.of(checkIn));

        CheckIn result = checkInService.getCheckInById("CHK-12345678");

        assertNotNull(result);
        assertEquals("CHK-12345678", result.getCheckInId());
        
        verify(checkInRepository).findById(eq("CHK-12345678"));
    }

    @Test
    void getCheckInById_WhenCheckInNotFound_ShouldThrowException() {
        when(checkInRepository.findById(anyString())).thenReturn(Optional.empty());

        assertThrows(CheckInNotFoundException.class, 
                    () -> checkInService.getCheckInById("CHK-INVALID"));
    }

    @Test
    void getCheckInsForPassenger_WhenAuthorized_ShouldReturnSummaries() {
        setAuthentication("P123456");

        Reservation reservation = new Reservation();
        reservation.setPassengerId("P123456");
        reservation.setFlightId("SK1234");
        reservation.setStatus("ACTIVE");
        reservation.setCreatedAt(LocalDateTime.now());
        reservation.setUpdatedAt(LocalDateTime.now());

        when(reservationRepository.findByPassengerIdAndStatus(eq("P123456"), eq("ACTIVE")))
                .thenReturn(List.of(reservation));
        when(checkInRepository.findFirstByPassengerIdAndFlightIdOrderByCreatedAtDesc(
                eq("P123456"), eq("SK1234")))
                .thenReturn(Optional.of(checkIn));

        List<PassengerCheckInSummaryDTO> result = checkInService.getCheckInsForPassenger("P123456");

        assertEquals(1, result.size());
        assertEquals("CHK-12345678", result.get(0).getCheckInId());
        assertEquals("SK1234", result.get(0).getFlightId());
    }

    @Test
    void getCheckInsForPassenger_WhenUnauthorized_ShouldThrowException() {
        setAuthentication("P999999");

        assertThrows(UnauthorizedException.class,
                () -> checkInService.getCheckInsForPassenger("P123456"));
    }

    private void setAuthentication(String passengerId) {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(passengerId);

        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);
    }
}
