package com.swp391.api.modules.report.dto;

import java.math.BigDecimal;

/**
 * DTO chứa thông tin thống kê món ăn bán chạy nhất.
 */
public class TopSellingItemDto {
    private String name;
    private long quantity;
    private BigDecimal revenue;

    public TopSellingItemDto() {
    }

    public TopSellingItemDto(String name, long quantity, BigDecimal revenue) {
        this.name = name;
        this.quantity = quantity;
        this.revenue = revenue;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getQuantity() {
        return quantity;
    }

    public void setQuantity(long quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getRevenue() {
        return revenue;
    }

    public void setRevenue(BigDecimal revenue) {
        this.revenue = revenue;
    }
}
