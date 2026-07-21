package com.swp391.api.modules.payment.repository;

import com.swp391.api.modules.payment.entity.Payment;
import com.swp391.api.modules.payment.entity.PaymentStatus;
import java.util.Optional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findFirstByOrder_IdOrderByCreatedAtDesc(Long orderId);

    Optional<Payment> findFirstByOrder_IdAndStatusOrderByCreatedAtDesc(Long orderId, PaymentStatus status);

    Optional<Payment> findFirstByBill_IdOrderByCreatedAtDesc(Long billId);

    Optional<Payment> findByPaymentCode(String paymentCode);

    boolean existsByProviderTransactionId(String providerTransactionId);

    // Tìm danh sách thanh toán thành công trong khoảng thời gian [start, end)
    // Đầu vào: status (Trạng thái thanh toán), start (Thời điểm bắt đầu), end (Thời điểm kết thúc độc quyền)
    // Trả về: Danh sách các bản ghi thanh toán thỏa mãn điều kiện
    List<Payment> findByStatusAndPaidAtGreaterThanEqualAndPaidAtLessThan(
        PaymentStatus status, LocalDateTime start, LocalDateTime end
    );
}
