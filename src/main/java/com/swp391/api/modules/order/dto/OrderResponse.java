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
        String tableNumber,
        String tableName,
        String tableStatus,
        Long waiterId,
        String waiterName,
        String publicAccessToken,
        String qrPath,
        OrderStatus status,
        String serviceStatus,
        String note,
        BigDecimal total,
        List<OrderItemResponse> items,
        LocalDateTime closedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
