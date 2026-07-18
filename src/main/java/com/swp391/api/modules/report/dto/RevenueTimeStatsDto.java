package com.swp391.api.modules.report.dto;

import java.math.BigDecimal;

/**
 * Đối tượng DTO đại diện cho thống kê doanh thu tại một mốc thời gian cụ thể (ngày/tháng/năm).
 */
public class RevenueTimeStatsDto {
    // Nhãn hiển thị thời gian (ví dụ: "2026-07-17", "2026-07", "2026")
    private String timeLabel;

    // Tổng doanh thu thu được trong mốc thời gian này
    private BigDecimal revenue;

    // Tổng số lượng giao dịch thành công trong mốc thời gian này
    private long transactionCount;

    public RevenueTimeStatsDto() {
    }

    public RevenueTimeStatsDto(String timeLabel, BigDecimal revenue, long transactionCount) {
        this.timeLabel = timeLabel;
        this.revenue = revenue;
        this.transactionCount = transactionCount;
    }

    public String getTimeLabel() {
        return timeLabel;
    }

    public void setTimeLabel(String timeLabel) {
        this.timeLabel = timeLabel;
    }

    public BigDecimal getRevenue() {
        return revenue;
    }

    public void setRevenue(BigDecimal revenue) {
        this.revenue = revenue;
    }

    public long getTransactionCount() {
        return transactionCount;
    }

    public void setTransactionCount(long transactionCount) {
        this.transactionCount = transactionCount;
    }
}
