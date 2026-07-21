package com.swp391.api.modules.qr.dto;

public class QrUpdateItemRequest {
    private Integer quantity;
    private String note;

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
