package com.swp391.api.modules.report.controller;

import com.swp391.api.modules.report.dto.DashboardStatsResponse;
import com.swp391.api.modules.report.dto.GroupByMode;
import com.swp391.api.modules.report.dto.RevenueStatsResponse;
import com.swp391.api.modules.report.service.ReportService;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller cung cấp các API báo cáo số liệu doanh thu cho nhà hàng.
 * Chỉ cho phép ADMIN và MANAGER truy cập thông tin tài chính nhạy cảm.
 */
@RestController
@RequestMapping("/api/dashboard") // Giữ nguyên URI mapping để tương thích ngược với frontend
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    /**
     * Lấy dữ liệu thống kê hoạt động tổng hợp cho Dashboard trang chủ.
     * Chỉ cho phép ADMIN và MANAGER truy cập thông tin kinh doanh.
     */
    @GetMapping("/overview")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<?> getDashboardOverview() {
        try {
            DashboardStatsResponse overview = reportService.getDashboardOverview();
            return ResponseEntity.ok(overview);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("message", "Internal server error during dashboard overview processing: " + e.getMessage()));
        }
    }

    /**
     * Lấy dữ liệu thống kê doanh thu dòng tiền.
     *
     * @param startDate Ngày bắt đầu (định dạng YYYY-MM-DD, mặc định: 30 ngày trước)
     * @param endDate Ngày kết thúc (định dạng YYYY-MM-DD, mặc định: ngày hiện tại)
     * @param groupBy Chế độ nhóm (day, month, year, mặc định: day)
     * @return Báo cáo doanh thu và dữ liệu biểu đồ cột
     */
    @GetMapping("/revenue")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<?> getRevenueStatistics(
            @RequestParam(value = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(value = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(value = "groupBy", defaultValue = "day") String groupBy) {

        try {
            // Thiết lập giá trị mặc định nếu tham số bị bỏ trống
            if (startDate == null) {
                startDate = LocalDate.now().minusDays(30);
            }
            if (endDate == null) {
                endDate = LocalDate.now();
            }

            // Chuyển đổi chuỗi groupBy sang enum GroupByMode tương ứng
            GroupByMode mode;
            try {
                mode = GroupByMode.valueOf(groupBy.toUpperCase());
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body(Map.of("message", "Group by mode 'groupBy' only accepts: hour, day, month or year"));
            }

            // Gọi service xử lý nghiệp vụ
            RevenueStatsResponse stats = reportService.getRevenueStatistics(startDate, endDate, mode);
            return ResponseEntity.ok(stats);

        } catch (IllegalArgumentException e) {
            // Bắt các lỗi validate khoảng ngày hoặc hạn mức dung lượng
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            // Phòng tránh lỗi runtime không lường trước
            return ResponseEntity.internalServerError().body(Map.of("message", "Internal server error during statistics processing: " + e.getMessage()));
        }
    }
}
