package com.swp391.api.modules.payment.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BillResponse(
        Long id,
        String billCode,
        Long reservationId,
        String status,
        Long promotionId,
        String promotionCode,
        String promotionName,
        BigDecimal subtotal,
        BigDecimal discountAmount,
        BigDecimal total,
        String paymentProvider,
        String paymentStatus,
        String paymentCode,
        String paymentQrImageUrl,
        LocalDateTime paidAt,
        LocalDateTime lockedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
