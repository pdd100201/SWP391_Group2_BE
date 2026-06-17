package com.swp391.api.modules.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * DTO dùng để cập nhật số lượng tồn kho của một mặt hàng.
 *
 * <p>Tách riêng khỏi request tạo mới để:
 * <ul>
 *   <li>Đơn giản hóa API - chỉ cho phép cập nhật số lượng và ghi chú</li>
 *   <li>Không cho phép thay đổi tên, danh mục, đơn vị qua endpoint này</li>
 * </ul>
 * </p>
 */
public class UpdateQuantityRequest {

    /**
     * Số lượng mới của mặt hàng trong kho.
     * Bắt buộc phải cung cấp và phải >= 0.
     * Hệ thống sẽ dùng giá trị này thay thế hoàn toàn số lượng cũ (không phải cộng thêm).
     */
    @NotNull(message = "Quantity is required")
//    @Min(value = 0, message = "Quantity must be >= 0")
    private Double quantity;

    /**
     * Ghi chú lý do cập nhật số lượng (tùy chọn).
     * Ví dụ: "Nhập hàng từ Fresh Farm Co.", "Xuất kho cho bữa tối ngày 09/06".
     * Hiện tại chưa lưu vào DB nhưng dành cho tương lai khi thêm lịch sử nhập xuất.
     */
    private String note;

    /** @return Số lượng mới */
    public Double getQuantity() { return quantity; }
    /** @param quantity Số lượng mới cần set */
    public void setQuantity(Double quantity) { this.quantity = quantity; }

    /** @return Ghi chú kèm theo */
    public String getNote() { return note; }
    /** @param note Ghi chú cần set */
    public void setNote(String note) { this.note = note; }
}
