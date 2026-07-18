package com.swp391.api.modules.report.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Lớp phản hồi dữ liệu thống kê doanh thu đơn giản cho Báo cáo doanh thu (Reports).
 * Lớp này chỉ trả về tổng tiền, số giao dịch trong kỳ và mảng dữ liệu biểu đồ.
 */
public class RevenueStatsResponse {
    
    // Tổng doanh thu thực tế thu được trong khoảng thời gian lọc (kỳ này)
    private BigDecimal totalRevenuePeriod;

    // Tổng số giao dịch thanh toán thành công trong khoảng thời gian lọc
    private long transactionCountPeriod;

    // Danh sách thống kê chia nhỏ theo mốc thời gian để vẽ biểu đồ và bảng
    private List<RevenueTimeStatsDto> chartData;

    public RevenueStatsResponse() {
    }

    public RevenueStatsResponse(BigDecimal totalRevenuePeriod, long transactionCountPeriod, List<RevenueTimeStatsDto> chartData) {
        this.totalRevenuePeriod = totalRevenuePeriod;
        this.transactionCountPeriod = transactionCountPeriod;
        this.chartData = chartData;
    }

    public BigDecimal getTotalRevenuePeriod() {
        return totalRevenuePeriod;
    }

    public void setTotalRevenuePeriod(BigDecimal totalRevenuePeriod) {
        this.totalRevenuePeriod = totalRevenuePeriod;
    }

    public long getTransactionCountPeriod() {
        return transactionCountPeriod;
    }

    public void setTransactionCountPeriod(long transactionCountPeriod) {
        this.transactionCountPeriod = transactionCountPeriod;
    }

    public List<RevenueTimeStatsDto> getChartData() {
        return chartData;
    }

    public void setChartData(List<RevenueTimeStatsDto> chartData) {
        this.chartData = chartData;
    }
}
