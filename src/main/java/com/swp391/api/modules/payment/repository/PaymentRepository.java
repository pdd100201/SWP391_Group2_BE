package com.swp391.api.modules.payment.repository;

import com.swp391.api.modules.payment.entity.Payment;
import com.swp391.api.modules.payment.entity.PaymentStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findFirstByOrder_IdOrderByCreatedAtDesc(Long orderId);

    Optional<Payment> findFirstByOrder_IdAndStatusOrderByCreatedAtDesc(Long orderId, PaymentStatus status);

    Optional<Payment> findByPaymentCode(String paymentCode);

    boolean existsByProviderTransactionId(String providerTransactionId);
}
