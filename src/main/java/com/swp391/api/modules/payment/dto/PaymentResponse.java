package com.swp391.api.modules.payment.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentResponse(
        Long id,
        Long orderId,
        String orderCode,
        String provider,
        String status,
        BigDecimal amount,
        String paymentCode,
        String qrImageUrl,
        String checkoutUrl,
        String providerTransactionId,
        LocalDateTime paidAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
