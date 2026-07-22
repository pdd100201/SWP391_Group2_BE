package com.swp391.api.modules.report.dto;

import java.math.BigDecimal;

/**
 * DTO chứa thông tin thống kê phân phối của từng phương thức thanh toán.
 */
public class PaymentMethodBreakdownDto {
    private String provider;
    private long count;
    private BigDecimal totalAmount;

    public PaymentMethodBreakdownDto() {
    }

    public PaymentMethodBreakdownDto(String provider, long count, BigDecimal totalAmount) {
        this.provider = provider;
        this.count = count;
        this.totalAmount = totalAmount;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public long getCount() {
        return count;
    }

    public void setCount(long count) {
        this.count = count;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }
}
