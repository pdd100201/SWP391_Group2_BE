package com.swp391.api.modules.report.dto;

import java.math.BigDecimal;

/**
 * DTO đại diện cho một món ăn trong đơn hàng được thanh toán, dùng trong báo cáo chi tiết.
 */
public class OrderItemDto {
    private Long id;
    private String menuItemName;
    private BigDecimal unitPrice;
    private BigDecimal subtotal;
    private Integer quantity;
    private String note;

    public OrderItemDto() {
    }

    public OrderItemDto(Long id, String menuItemName, BigDecimal unitPrice, BigDecimal subtotal, Integer quantity, String note) {
        this.id = id;
        this.menuItemName = menuItemName;
        this.unitPrice = unitPrice;
        this.subtotal = subtotal;
        this.quantity = quantity;
        this.note = note;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getMenuItemName() {
        return menuItemName;
    }

    public void setMenuItemName(String menuItemName) {
        this.menuItemName = menuItemName;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(BigDecimal subtotal) {
        this.subtotal = subtotal;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
