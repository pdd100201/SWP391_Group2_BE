package com.swp391.api.modules.report.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO đại diện cho thông tin chi tiết một giao dịch thanh toán trong báo cáo doanh thu.
 */
public class PaymentTransactionDto {
    private Long id;
    private String paymentCode;
    private Long orderId;
    private String orderCode;
    private BigDecimal amount;
    private BigDecimal subtotal;
    private BigDecimal discountAmount;
    private Long promotionId;
    private String promotionCode;
    private String promotionName;
    private BigDecimal total;
    private String provider;
    private LocalDateTime paidAt;
    private String guestName;
    private String tableNames;
    private String waiterName;
    private List<OrderItemDto> items;

    public PaymentTransactionDto() {
    }

    public PaymentTransactionDto(Long id, String paymentCode, Long orderId, String orderCode, BigDecimal amount, String provider, LocalDateTime paidAt, String guestName, String tableNames, String waiterName, List<OrderItemDto> items) {
        this.id = id;
        this.paymentCode = paymentCode;
        this.orderId = orderId;
        this.orderCode = orderCode;
        this.amount = amount;
        this.provider = provider;
        this.paidAt = paidAt;
        this.guestName = guestName;
        this.tableNames = tableNames;
        this.waiterName = waiterName;
        this.items = items;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPaymentCode() {
        return paymentCode;
    }

    public void setPaymentCode(String paymentCode) {
        this.paymentCode = paymentCode;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public String getOrderCode() {
        return orderCode;
    }

    public void setOrderCode(String orderCode) {
        this.orderCode = orderCode;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(BigDecimal subtotal) {
        this.subtotal = subtotal;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public void setDiscountAmount(BigDecimal discountAmount) {
        this.discountAmount = discountAmount;
    }

    public Long getPromotionId() { return promotionId; }
    public void setPromotionId(Long promotionId) { this.promotionId = promotionId; }
    public String getPromotionCode() { return promotionCode; }
    public void setPromotionCode(String promotionCode) { this.promotionCode = promotionCode; }
    public String getPromotionName() { return promotionName; }
    public void setPromotionName(String promotionName) { this.promotionName = promotionName; }

    public BigDecimal getTotal() {
        return total;
    }

    public void setTotal(BigDecimal total) {
        this.total = total;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public LocalDateTime getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(LocalDateTime paidAt) {
        this.paidAt = paidAt;
    }

    public String getGuestName() {
        return guestName;
    }

    public void setGuestName(String guestName) {
        this.guestName = guestName;
    }

    public String getTableNames() {
        return tableNames;
    }

    public void setTableNames(String tableNames) {
        this.tableNames = tableNames;
    }

    public String getWaiterName() {
        return waiterName;
    }

    public void setWaiterName(String waiterName) {
        this.waiterName = waiterName;
    }

    public List<OrderItemDto> getItems() {
        return items;
    }

    public void setItems(List<OrderItemDto> items) {
        this.items = items;
    }
}
