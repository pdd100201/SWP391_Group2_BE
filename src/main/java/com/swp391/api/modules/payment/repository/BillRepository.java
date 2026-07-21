package com.swp391.api.modules.payment.repository;

import com.swp391.api.modules.payment.entity.Bill;
import com.swp391.api.modules.payment.entity.BillStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillRepository extends JpaRepository<Bill, Long> {
    Optional<Bill> findByReservation_ReservationId(Long reservationId);
    List<Bill> findByStatusOrderByPaidAtDesc(BillStatus status);
    List<Bill> findAllByOrderByUpdatedAtDesc();
}
