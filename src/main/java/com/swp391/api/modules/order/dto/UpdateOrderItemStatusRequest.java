package com.swp391.api.modules.order.dto;

import com.swp391.api.modules.order.entity.OrderItemStatus;
import jakarta.validation.constraints.NotNull;

// Staff payload for moving an item through kitchen/service statuses.
public class UpdateOrderItemStatusRequest {
    @NotNull(message = "Item status is required")
    private OrderItemStatus status;

    public OrderItemStatus getStatus() { return status; }
    public void setStatus(OrderItemStatus status) { this.status = status; }
}
