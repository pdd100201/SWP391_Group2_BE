package com.swp391.api.modules.order.entity;

// Vòng đời món: nháp -> xác nhận -> chế biến -> sẵn sàng -> đã phục vụ.
// CANCELLED là nhánh kết thúc khi nhân viên hủy món hợp lệ.
public enum OrderItemStatus {
    DRAFT,
    CONFIRMED,
    PREPARING,
    READY,
    SERVED,
    CANCELLED
}
