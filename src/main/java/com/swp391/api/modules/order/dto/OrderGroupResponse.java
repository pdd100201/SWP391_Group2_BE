package com.swp391.api.modules.order.dto;

import com.swp391.api.modules.payment.dto.BillResponse;
import com.swp391.api.modules.reservation.entity.ReservationStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderGroupResponse(
        Long reservationId,
        String reservationGuestName,
        ReservationStatus reservationStatus,
        BigDecimal subtotal,
        BigDecimal discountAmount,
        BigDecimal total,
        BillResponse bill,
        List<OrderResponse> orders,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
