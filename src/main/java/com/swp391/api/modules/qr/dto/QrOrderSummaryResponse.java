package com.swp391.api.modules.qr.dto;

import java.time.LocalDateTime;

public class QrOrderSummaryResponse {

    private Long orderId;
    private String orderCode;
    private Long tableId;
    private String tableName;
    private String guestName;
    private String status;
    private String serviceStatus;
    private Double totalAmount;
    private LocalDateTime createdAt;
    private Integer itemCount;

    public QrOrderSummaryResponse() {}

    public QrOrderSummaryResponse(Long orderId, String orderCode, Long tableId, String tableName, String guestName,
                                   String status, String serviceStatus, Double totalAmount, LocalDateTime createdAt, Integer itemCount) {
        this.orderId = orderId;
        this.orderCode = orderCode;
        this.tableId = tableId;
        this.tableName = tableName;
        this.guestName = guestName;
        this.status = status;
        this.serviceStatus = serviceStatus;
        this.totalAmount = totalAmount;
        this.createdAt = createdAt;
        this.itemCount = itemCount;
    }

    public String getOrderCode() { return orderCode; }
    public void setOrderCode(String orderCode) { this.orderCode = orderCode; }

    public String getTableName() { return tableName; }
    public void setTableName(String tableName) { this.tableName = tableName; }

    public String getGuestName() { return guestName; }
    public void setGuestName(String guestName) { this.guestName = guestName; }

    public String getServiceStatus() { return serviceStatus; }
    public void setServiceStatus(String serviceStatus) { this.serviceStatus = serviceStatus; }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }

    public Long getTableId() { return tableId; }
    public void setTableId(Long tableId) { this.tableId = tableId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Double getTotalAmount() { return totalAmount; }
    public void setTotalAmount(Double totalAmount) { this.totalAmount = totalAmount; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public Integer getItemCount() { return itemCount; }
    public void setItemCount(Integer itemCount) { this.itemCount = itemCount; }
}
