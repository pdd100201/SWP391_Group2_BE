package com.swp391.api.modules.report.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Lớp phản hồi dữ liệu thống kê doanh thu nâng cao cho Báo cáo doanh thu (Reports).
 * Chứa tổng số liệu, mảng dữ liệu biểu đồ xu hướng, chi tiết giao dịch, phân phối phương thức và top bán chạy.
 */
public class RevenueStatsResponse {
    
    // Tổng doanh thu thực tế thu được trong khoảng thời gian lọc (kỳ này)
    private BigDecimal totalRevenuePeriod;

    // Tổng số giao dịch thanh toán thành công trong khoảng thời gian lọc
    private long transactionCountPeriod;

    // Danh sách thống kê chia nhỏ theo mốc thời gian để vẽ biểu đồ và bảng
    private List<RevenueTimeStatsDto> chartData;

    // Chi tiết tất cả giao dịch trong kỳ lọc
    private List<PaymentTransactionDto> transactions;

    // Danh sách món ăn bán chạy nhất trong kỳ lọc
    private List<TopSellingItemDto> topSellingItems;

    // Danh sách phân phối các phương thức thanh toán
    private List<PaymentMethodBreakdownDto> paymentMethods;

    // Giá trị đơn hàng trung bình (AOV)
    private BigDecimal averageOrderValue;

    public RevenueStatsResponse() {
    }

    public RevenueStatsResponse(BigDecimal totalRevenuePeriod, long transactionCountPeriod, List<RevenueTimeStatsDto> chartData,
                                List<PaymentTransactionDto> transactions, List<TopSellingItemDto> topSellingItems,
                                List<PaymentMethodBreakdownDto> paymentMethods, BigDecimal averageOrderValue) {
        this.totalRevenuePeriod = totalRevenuePeriod;
        this.transactionCountPeriod = transactionCountPeriod;
        this.chartData = chartData;
        this.transactions = transactions;
        this.topSellingItems = topSellingItems;
        this.paymentMethods = paymentMethods;
        this.averageOrderValue = averageOrderValue;
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

    public List<PaymentTransactionDto> getTransactions() {
        return transactions;
    }

    public void setTransactions(List<PaymentTransactionDto> transactions) {
        this.transactions = transactions;
    }

    public List<TopSellingItemDto> getTopSellingItems() {
        return topSellingItems;
    }

    public void setTopSellingItems(List<TopSellingItemDto> topSellingItems) {
        this.topSellingItems = topSellingItems;
    }

    public List<PaymentMethodBreakdownDto> getPaymentMethods() {
        return paymentMethods;
    }

    public void setPaymentMethods(List<PaymentMethodBreakdownDto> paymentMethods) {
        this.paymentMethods = paymentMethods;
    }

    public BigDecimal getAverageOrderValue() {
        return averageOrderValue;
    }

    public void setAverageOrderValue(BigDecimal averageOrderValue) {
        this.averageOrderValue = averageOrderValue;
    }
}
