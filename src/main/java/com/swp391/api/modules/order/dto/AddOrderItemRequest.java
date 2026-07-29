package com.swp391.api.modules.order.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// Dữ liệu thêm một món, dùng chung cho màn nhân viên và trang gọi món của khách.
public class AddOrderItemRequest {
    // Món bắt buộc phải tồn tại và đang hoạt động; service kiểm tra điều kiện này.
    @NotNull(message = "Menu item is required")
    private Long menuItemId;

    // Một lần thêm tối thiểu 1 và tối đa 99 phần.
    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be at least 1")
    @Max(value = 99, message = "Quantity must not exceed 99")
    private Integer quantity;

    // Ghi chú tùy chọn như ít cay/không hành, tối đa 500 ký tự.
    @Size(max = 500, message = "Item note must not exceed 500 characters")
    private String note;

    public Long getMenuItemId() { return menuItemId; }
    public void setMenuItemId(Long menuItemId) { this.menuItemId = menuItemId; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
