package com.swp391.api.modules.order.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// Payload for changing quantity/note before an item can no longer be edited.
public class UpdateOrderItemRequest {
    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be at least 1")
    @Max(value = 99, message = "Quantity must not exceed 99")
    private Integer quantity;

    @Size(max = 500, message = "Item note must not exceed 500 characters")
    private String note;

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
