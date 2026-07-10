package com.swp391.api.modules.order.dto;

import com.swp391.api.modules.order.entity.OrderStatus;
import com.swp391.api.modules.reservation.entity.ReservationStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderResponse(
        Long id,
        String orderCode,
        Long reservationId,
        String reservationGuestName,
        ReservationStatus reservationStatus,
        Long tableId,
        List<Long> tableIds,
        String tableNumber,
        List<String> tableNumbers,
        String tableName,
        List<String> tableNames,
        String tableStatus,
        Long waiterId,
        String waiterName,
        String publicAccessToken,
        String qrPath,
        OrderStatus status,
        String serviceStatus,
        String note,
        Long promotionId,
        String promotionCode,
        String promotionName,
        BigDecimal subtotal,
        BigDecimal discountAmount,
        BigDecimal total,
        Long paymentId,
        String paymentProvider,
        String paymentStatus,
        String paymentCode,
        String paymentQrImageUrl,
        LocalDateTime paidAt,
        List<OrderItemResponse> items,
        LocalDateTime closedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
