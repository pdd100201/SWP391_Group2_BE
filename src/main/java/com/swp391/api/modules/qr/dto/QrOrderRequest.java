package com.swp391.api.modules.qr.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public class QrOrderRequest {

    @NotBlank
    private String sessionToken;

    @NotEmpty
    private List<OrderItemDto> items;

    public String getSessionToken() { return sessionToken; }
    public void setSessionToken(String sessionToken) { this.sessionToken = sessionToken; }

    public List<OrderItemDto> getItems() { return items; }
    public void setItems(List<OrderItemDto> items) { this.items = items; }

    public static class OrderItemDto {

        @NotNull
        private Long itemId;

        @NotNull
        @Positive
        private Integer quantity;

        private String note;

        public Long getItemId() { return itemId; }
        public void setItemId(Long itemId) { this.itemId = itemId; }

        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }

        public String getNote() { return note; }
        public void setNote(String note) { this.note = note; }
    }
}
