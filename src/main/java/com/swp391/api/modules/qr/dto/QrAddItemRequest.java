package com.swp391.api.modules.qr.dto;

public class QrAddItemRequest {
    private Long menuItemId;
    private Integer quantity;

    public Long getMenuItemId() { return menuItemId; }
    public void setMenuItemId(Long menuItemId) { this.menuItemId = menuItemId; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
}
