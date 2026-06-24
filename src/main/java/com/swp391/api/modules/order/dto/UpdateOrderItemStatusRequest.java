package com.swp391.api.modules.order.dto;

import com.swp391.api.modules.order.entity.OrderItemStatus;
import jakarta.validation.constraints.NotNull;

public class UpdateOrderItemStatusRequest {
    @NotNull(message = "Item status is required")
    private OrderItemStatus status;

    public OrderItemStatus getStatus() { return status; }
    public void setStatus(OrderItemStatus status) { this.status = status; }
}
