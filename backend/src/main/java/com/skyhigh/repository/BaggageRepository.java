package com.skyhigh.repository;

import com.skyhigh.entity.Baggage;
import com.skyhigh.enums.BaggageType;
import com.skyhigh.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BaggageRepository extends JpaRepository<Baggage, Long> {

    List<Baggage> findByCheckInId(String checkInId);

    Optional<Baggage> findFirstByCheckInId(String checkInId);

    List<Baggage> findByPaymentStatus(PaymentStatus paymentStatus);

    List<Baggage> findByBaggageType(BaggageType baggageType);

    @Query("SELECT b FROM Baggage b WHERE b.checkInId = :checkInId AND b.paymentStatus = :paymentStatus")
    List<Baggage> findByCheckInIdAndPaymentStatus(
        @Param("checkInId") String checkInId,
        @Param("paymentStatus") PaymentStatus paymentStatus
    );

    @Query("SELECT COUNT(b) FROM Baggage b WHERE b.checkInId = :checkInId")
    long countByCheckInId(@Param("checkInId") String checkInId);

    @Query("SELECT SUM(b.excessFee) FROM Baggage b WHERE b.checkInId = :checkInId AND b.paymentStatus = 'PAID'")
    Double sumExcessFeesByCheckInId(@Param("checkInId") String checkInId);
}
