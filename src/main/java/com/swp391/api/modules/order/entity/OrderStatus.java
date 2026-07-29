package com.swp391.api.modules.order.entity;

// Vòng đời order: OPEN cho phép thao tác, CLOSED đã hoàn tất, CANCELLED đã hủy.
public enum OrderStatus {
    OPEN,
    CLOSED,
    CANCELLED
}
