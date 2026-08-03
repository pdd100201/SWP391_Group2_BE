package com.swp391.api.modules.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public class VoidOrderItemRequest {
    @NotBlank(message = "Void reason is required")
    @Size(max = 255, message = "Void reason must be at most 255 characters")
    private String reason;

    @Min(value = 1, message = "Void quantity must be at least 1")
    private Integer quantity = 1;

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
}
