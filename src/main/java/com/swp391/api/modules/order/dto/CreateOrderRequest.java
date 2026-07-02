package com.swp391.api.modules.order.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class CreateOrderRequest {
    @NotNull(message = "Reservation is required")
    private Long reservationId;

    @Size(max = 2000, message = "Order note must not exceed 2000 characters")
    private String note;

    public Long getReservationId() { return reservationId; }
    public void setReservationId(Long reservationId) { this.reservationId = reservationId; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
