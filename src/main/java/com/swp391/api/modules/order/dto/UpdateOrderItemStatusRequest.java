package com.swp391.api.modules.order.dto;

import com.swp391.api.modules.order.entity.OrderItemStatus;
import jakarta.validation.constraints.NotNull;

// Payload nhân viên dùng để chuyển món qua các bước bếp/phục vụ.
public class UpdateOrderItemStatusRequest {
    // Service chỉ chấp nhận bước kế tiếp, không cho bỏ qua hoặc quay lùi.
    @NotNull(message = "Item status is required")
    private OrderItemStatus status;

    public OrderItemStatus getStatus() { return status; }
    public void setStatus(OrderItemStatus status) { this.status = status; }
}
