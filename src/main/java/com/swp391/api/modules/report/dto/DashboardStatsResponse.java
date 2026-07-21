package com.swp391.api.modules.report.dto;

import java.math.BigDecimal;

/**
 * DTO chứa thông tin thống kê hoạt động tổng quan cho Dashboard trang chủ.
 */
public class DashboardStatsResponse {
    private BigDecimal totalRevenue;
    private long successfulTransactions;
    private long totalTables;
    private long totalReservations;
    private long totalMenuItems;
    private long totalStaff;

    public DashboardStatsResponse() {
    }

    public DashboardStatsResponse(BigDecimal totalRevenue, long successfulTransactions, long totalTables, 
                                  long totalReservations, long totalMenuItems, long totalStaff) {
        this.totalRevenue = totalRevenue;
        this.successfulTransactions = successfulTransactions;
        this.totalTables = totalTables;
        this.totalReservations = totalReservations;
        this.totalMenuItems = totalMenuItems;
        this.totalStaff = totalStaff;
    }

    public BigDecimal getTotalRevenue() {
        return totalRevenue;
    }

    public void setTotalRevenue(BigDecimal totalRevenue) {
        this.totalRevenue = totalRevenue;
    }

    public long getSuccessfulTransactions() {
        return successfulTransactions;
    }

    public void setSuccessfulTransactions(long successfulTransactions) {
        this.successfulTransactions = successfulTransactions;
    }

    public long getTotalTables() {
        return totalTables;
    }

    public void setTotalTables(long totalTables) {
        this.totalTables = totalTables;
    }

    public long getTotalReservations() {
        return totalReservations;
    }

    public void setTotalReservations(long totalReservations) {
        this.totalReservations = totalReservations;
    }

    public long getTotalMenuItems() {
        return totalMenuItems;
    }

    public void setTotalMenuItems(long totalMenuItems) {
        this.totalMenuItems = totalMenuItems;
    }

    public long getTotalStaff() {
        return totalStaff;
    }

    public void setTotalStaff(long totalStaff) {
        this.totalStaff = totalStaff;
    }
}
