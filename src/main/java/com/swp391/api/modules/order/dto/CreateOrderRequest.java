package com.swp391.api.modules.order.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// Yêu cầu nhân viên mở/đồng bộ order từ một reservation đã check-in.
public class CreateOrderRequest {
    // Backend khóa reservation khi tạo để tránh hai người tạo trùng order cho cùng bàn.
    @NotNull(message = "Reservation is required")
    private Long reservationId;

    // Ghi chú chung của order là tùy chọn và tối đa 2000 ký tự.
    @Size(max = 2000, message = "Order note must not exceed 2000 characters")
    private String note;

    public Long getReservationId() { return reservationId; }
    public void setReservationId(Long reservationId) { this.reservationId = reservationId; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
