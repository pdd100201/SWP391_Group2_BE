package com.swp391.api.modules.order.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// Dữ liệu thay đổi số lượng/ghi chú trước khi món đi quá giai đoạn cho phép chỉnh sửa.
public class UpdateOrderItemRequest {
    // Không cập nhật về 0; muốn bỏ món phải gọi endpoint DELETE.
    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be at least 1")
    @Max(value = 99, message = "Quantity must not exceed 99")
    private Integer quantity;

    // Ghi chú áp cùng giới hạn 500 ký tự như lúc thêm món.
    @Size(max = 500, message = "Item note must not exceed 500 characters")
    private String note;

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
