package com.skyhigh.service;

import com.skyhigh.dto.BaggageDetailsDTO;
import com.skyhigh.dto.BaggageResponseDTO;
import com.skyhigh.entity.Baggage;
import com.skyhigh.enums.BaggageType;
import com.skyhigh.enums.PaymentStatus;
import com.skyhigh.exception.BaggageNotFoundException;
import com.skyhigh.repository.BaggageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BaggageServiceImplTest {

    @Mock
    private BaggageRepository baggageRepository;

    private WeightService weightService;
    private BaggageServiceImpl baggageService;

    @BeforeEach
    void setUp() {
        weightService = new WeightService();
        baggageService = new BaggageServiceImpl(baggageRepository, weightService);
        ReflectionTestUtils.setField(baggageService, "maxAllowedWeight", new BigDecimal("25"));
        ReflectionTestUtils.setField(baggageService, "excessFeePerKg", new BigDecimal("10"));
    }

    @Test
    void addBaggage_whenNoExcess_shouldMarkPaidAndReturnMessage() {
        BaggageDetailsDTO details = BaggageDetailsDTO.builder()
                .weightKg(new BigDecimal("20.00"))
                .dimensions("20x20x20")
                .baggageType(BaggageType.CHECKED)
                .build();

        when(baggageRepository.findByCheckInId("CHK-1")).thenReturn(List.of());
        when(baggageRepository.save(any(Baggage.class))).thenAnswer(invocation -> {
            Baggage saved = invocation.getArgument(0);
            saved.setBaggageId(10L);
            return saved;
        });

        BaggageResponseDTO response = baggageService.addBaggage("CHK-1", details);

        assertNotNull(response);
        assertEquals(10L, response.getBaggageId());
        assertEquals(PaymentStatus.PAID, response.getPaymentStatus());
        assertEquals(new BigDecimal("0"), response.getExcessWeightKg());
        assertEquals(new BigDecimal("0"), response.getExcessFee());

        String expectedMessage = String.format(
                "Total weight: %.2f kg (Free allowance: 20 kg). %s",
                new BigDecimal("20.00"),
                "No excess fee"
        );
        assertEquals(expectedMessage, response.getMessage());

        ArgumentCaptor<Baggage> captor = ArgumentCaptor.forClass(Baggage.class);
        verify(baggageRepository).save(captor.capture());
        Baggage saved = captor.getValue();
        assertEquals("CHK-1", saved.getCheckInId());
        assertEquals(new BigDecimal("20.00"), saved.getWeightKg());
        assertEquals(BaggageType.CHECKED, saved.getBaggageType());
        assertEquals("20x20x20", saved.getDimensions());
        assertEquals(new BigDecimal("0"), saved.getExcessWeightKg());
        assertEquals(new BigDecimal("0"), saved.getExcessFee());
        assertEquals(PaymentStatus.PAID, saved.getPaymentStatus());
    }

    @Test
    void addBaggage_whenExcessFromThisBag_shouldChargeFeeAndMarkPending() {
        BaggageDetailsDTO details = BaggageDetailsDTO.builder()
                .weightKg(new BigDecimal("5.00"))
                .dimensions("30x20x10")
                .baggageType(BaggageType.CHECKED)
                .build();

        Baggage existing = new Baggage();
        existing.setWeightKg(new BigDecimal("24.00"));

        when(baggageRepository.findByCheckInId("CHK-2")).thenReturn(List.of(existing));
        when(baggageRepository.save(any(Baggage.class))).thenAnswer(invocation -> {
            Baggage saved = invocation.getArgument(0);
            saved.setBaggageId(11L);
            return saved;
        });

        BaggageResponseDTO response = baggageService.addBaggage("CHK-2", details);

        assertEquals(11L, response.getBaggageId());
        assertEquals(new BigDecimal("4.00"), response.getExcessWeightKg());
        assertEquals(new BigDecimal("40.00"), response.getExcessFee());
        assertEquals(PaymentStatus.PENDING, response.getPaymentStatus());
        assertTrue(response.getMessage().contains("Excess fee: $40.00"));
    }

    @Test
    void addBaggage_whenNegativeExcessCalculated_shouldClampToZero() {
        WeightService weightServiceMock = mock(WeightService.class);
        BaggageServiceImpl serviceWithMock = new BaggageServiceImpl(baggageRepository, weightServiceMock);
        ReflectionTestUtils.setField(serviceWithMock, "maxAllowedWeight", new BigDecimal("25"));
        ReflectionTestUtils.setField(serviceWithMock, "excessFeePerKg", new BigDecimal("10"));

        BaggageDetailsDTO details = BaggageDetailsDTO.builder()
                .weightKg(new BigDecimal("1.00"))
                .dimensions("10x10x10")
                .baggageType(BaggageType.CARRY_ON)
                .build();

        Baggage existing = new Baggage();
        existing.setWeightKg(new BigDecimal("10.00"));

        when(baggageRepository.findByCheckInId("CHK-3")).thenReturn(List.of(existing));
        when(weightServiceMock.calculateExcessWeight(any(BigDecimal.class), any(BigDecimal.class)))
                .thenReturn(new BigDecimal("2.00"), new BigDecimal("5.00"));
        when(baggageRepository.save(any(Baggage.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BaggageResponseDTO response = serviceWithMock.addBaggage("CHK-3", details);

        assertEquals(new BigDecimal("0"), response.getExcessWeightKg());
        assertEquals(new BigDecimal("0"), response.getExcessFee());
        assertEquals(PaymentStatus.PAID, response.getPaymentStatus());
    }

    @Test
    void getAllBaggageForCheckIn_shouldMapToDtos() {
        Baggage baggage = new Baggage();
        baggage.setBaggageId(99L);
        baggage.setCheckInId("CHK-4");
        baggage.setWeightKg(new BigDecimal("12.50"));
        baggage.setDimensions("40x30x20");
        baggage.setBaggageType(BaggageType.OVERSIZED);
        baggage.setExcessWeightKg(new BigDecimal("2.50"));
        baggage.setExcessFee(new BigDecimal("25.00"));
        baggage.setPaymentStatus(PaymentStatus.PAID);
        baggage.setPaymentTransactionId("TX-123");

        when(baggageRepository.findByCheckInId("CHK-4")).thenReturn(List.of(baggage));

        List<BaggageResponseDTO> result = baggageService.getAllBaggageForCheckIn("CHK-4");

        assertEquals(1, result.size());
        BaggageResponseDTO dto = result.get(0);
        assertEquals(99L, dto.getBaggageId());
        assertEquals("CHK-4", dto.getCheckInId());
        assertEquals(new BigDecimal("12.50"), dto.getWeightKg());
        assertEquals("40x30x20", dto.getDimensions());
        assertEquals(BaggageType.OVERSIZED, dto.getBaggageType());
        assertEquals(new BigDecimal("2.50"), dto.getExcessWeightKg());
        assertEquals(new BigDecimal("25.00"), dto.getExcessFee());
        assertEquals(PaymentStatus.PAID, dto.getPaymentStatus());
        assertEquals("TX-123", dto.getPaymentTransactionId());
    }

    @Test
    void getFirstBaggageByCheckInId_whenFound_shouldReturnEntity() {
        Baggage baggage = new Baggage();
        baggage.setBaggageId(5L);
        baggage.setCheckInId("CHK-5");

        when(baggageRepository.findFirstByCheckInId("CHK-5")).thenReturn(Optional.of(baggage));

        Baggage result = baggageService.getFirstBaggageByCheckInId("CHK-5");

        assertEquals(5L, result.getBaggageId());
    }

    @Test
    void getFirstBaggageByCheckInId_whenMissing_shouldThrow() {
        when(baggageRepository.findFirstByCheckInId("CHK-6")).thenReturn(Optional.empty());

        assertThrows(BaggageNotFoundException.class,
                () -> baggageService.getFirstBaggageByCheckInId("CHK-6"));
    }

    @Test
    void deleteBaggage_whenFound_shouldDelete() {
        Baggage baggage = new Baggage();
        baggage.setBaggageId(7L);

        when(baggageRepository.findById(7L)).thenReturn(Optional.of(baggage));

        baggageService.deleteBaggage(7L);

        verify(baggageRepository).delete(baggage);
    }

    @Test
    void deleteBaggage_whenMissing_shouldThrow() {
        when(baggageRepository.findById(anyLong())).thenReturn(Optional.empty());

        assertThrows(BaggageNotFoundException.class,
                () -> baggageService.deleteBaggage(999L));

        verify(baggageRepository, never()).delete(any(Baggage.class));
    }
}
