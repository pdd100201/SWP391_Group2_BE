package com.swp391.api.modules.report.service;

import com.swp391.api.modules.report.dto.GroupByMode;
import com.swp391.api.modules.report.dto.RevenueStatsResponse;
import java.time.LocalDate;

/**
 * Giao diện khai báo dịch vụ tính toán báo cáo báo cáo doanh thu dòng tiền.
 */
public interface ReportService {
    
    /**
     * Lấy báo cáo thống kê doanh thu theo thời gian và nhóm dữ liệu.
     *
     * @param startDate Ngày bắt đầu lọc
     * @param endDate Ngày kết thúc lọc
     * @param mode Chế độ nhóm (DAY, MONTH, YEAR)
     * @return Báo cáo thống kê chi tiết
     */
    RevenueStatsResponse getRevenueStatistics(LocalDate startDate, LocalDate endDate, GroupByMode mode);
}
