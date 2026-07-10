package com.swp391.api.modules.qr.dto;

import java.time.LocalDateTime;
import java.util.List;

public class QrOrderResponse {

    private Long orderId;
    private String orderCode;
    private Long tableId;
    private String status;
    private List<OrderItemDto> items;
    private Double totalAmount;
    private LocalDateTime createdAt;

    public QrOrderResponse() {}

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }

    public String getOrderCode() { return orderCode; }
    public void setOrderCode(String orderCode) { this.orderCode = orderCode; }

    public Long getTableId() { return tableId; }
    public void setTableId(Long tableId) { this.tableId = tableId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public List<OrderItemDto> getItems() { return items; }
    public void setItems(List<OrderItemDto> items) { this.items = items; }

    public Double getTotalAmount() { return totalAmount; }
    public void setTotalAmount(Double totalAmount) { this.totalAmount = totalAmount; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public static class OrderItemDto {
        private Long orderItemId;
        private Long itemId;
        private String itemName;
        private String itemImageUrl;
        private Integer quantity;
        private Double unitPrice;
        private Double subtotal;
        private String itemStatus;
        private String note;

        public OrderItemDto() {}

        public Long getOrderItemId() { return orderItemId; }
        public void setOrderItemId(Long orderItemId) { this.orderItemId = orderItemId; }

        public Long getItemId() { return itemId; }
        public void setItemId(Long itemId) { this.itemId = itemId; }

        public String getItemName() { return itemName; }
        public void setItemName(String itemName) { this.itemName = itemName; }

        public String getItemImageUrl() { return itemImageUrl; }
        public void setItemImageUrl(String itemImageUrl) { this.itemImageUrl = itemImageUrl; }

        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }

        public Double getUnitPrice() { return unitPrice; }
        public void setUnitPrice(Double unitPrice) { this.unitPrice = unitPrice; }

        public Double getSubtotal() { return subtotal; }
        public void setSubtotal(Double subtotal) { this.subtotal = subtotal; }

        public String getItemStatus() { return itemStatus; }
        public void setItemStatus(String itemStatus) { this.itemStatus = itemStatus; }

        public String getNote() { return note; }
        public void setNote(String note) { this.note = note; }
    }
}
