package com.swp391.api.modules.order.dto;

import com.swp391.api.modules.order.entity.OrderItemStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

// Dữ liệu một dòng món trả về bên trong OrderResponse.
// Tên, ảnh, danh mục và đơn giá là snapshot để lịch sử không đổi theo Menu hiện tại.
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
