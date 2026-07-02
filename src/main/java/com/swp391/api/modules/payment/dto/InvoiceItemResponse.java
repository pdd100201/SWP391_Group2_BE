package com.swp391.api.modules.payment.dto;

import com.swp391.api.modules.order.entity.OrderItemStatus;
import java.math.BigDecimal;

public record InvoiceItemResponse(
        Long id,
        String menuItemName,
        String category,
        BigDecimal unitPrice,
        Integer quantity,
        BigDecimal lineTotal,
        String note,
        OrderItemStatus status) {
}
