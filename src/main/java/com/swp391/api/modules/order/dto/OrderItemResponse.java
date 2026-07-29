package com.swp391.api.modules.order.dto;

import com.swp391.api.modules.order.entity.OrderItemStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

// One line item projection inside OrderResponse.
public record OrderItemResponse(
        Long id,
        Long menuItemId,
        String menuItemName,
        String menuItemImageUrl,
        String category,
        BigDecimal unitPrice,
        Integer quantity,
        BigDecimal lineTotal,
        String note,
        OrderItemStatus status,
        LocalDateTime submittedAt,
        String voidReason,
        LocalDateTime voidedAt,
        String voidedBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
