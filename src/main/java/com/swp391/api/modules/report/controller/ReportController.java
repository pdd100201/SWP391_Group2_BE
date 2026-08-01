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
 * ====================================================================================
 * BẢN HƯỚNG DẪN CHI TIẾT SPRING BOOT REST CONTROLLER (MODULE BÁO CÁO DOANH THU - REPORT)
 * ====================================================================================
 * VAI TRÒ CỦA CONTROLLER:
 *  - Cung cấp các đường dẫn API báo cáo tài chính, tổng quan doanh thu cho nhà hàng.
 *  - Phân quyền bảo mật: Sử dụng @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')") 
 *    để đảm bảo CHỈ CÓ ADMIN HOẶC MANAGER mới có quyền gọi API xem doanh thu nhạy cảm.
 * ====================================================================================
 */
@RestController
@RequestMapping("/api/dashboard") // Giữ nguyên URI mapping để tương thích ngược với frontend
public class ReportController {

    private final ReportService reportService;

    // Dependency Injection: Khởi tạo Service qua Constructor
    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    /**
     * API 1: Lấy dữ liệu thống kê hoạt động tổng hợp cho Trang chủ Dashboard
     * - URL: GET http://localhost:8080/api/dashboard/overview
     * 
     * QUY TRÌNH THỰC THI (STEP-BY-STEP FLOW):
     *  - Bước 1: React mở trang Dashboard -> Gửi HTTP GET /api/dashboard/overview kèm Token JWT.
     *  - Bước 2: Spring Security kiểm tra Token. Nếu không phải ADMIN/MANAGER -> Trả về 403 Forbidden.
     *  - Bước 3: Controller gọi reportService.getDashboardOverview() để đếm tổng số bàn, món ăn, nhân viên, đặt bàn.
     *  - Bước 4: Trả về JSON đối tượng DashboardStatsResponse (HTTP 200 OK).
     */
    @GetMapping("/overview")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<?> getDashboardOverview() {
        try {
            DashboardStatsResponse overview = reportService.getDashboardOverview();
            return ResponseEntity.ok(overview);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("message", "Lỗi nội bộ server trong quá trình xử lý tổng quan dashboard: " + e.getMessage()));
        }
    }

    /**
     * API 2: Lấy dữ liệu thống kê doanh thu dòng tiền vẽ biểu đồ
     * - URL: GET http://localhost:8080/api/dashboard/revenue?startDate=2026-07-26&endDate=2026-08-02&groupBy=day
     *
     * QUY TRÌNH THỰC THI (STEP-BY-STEP FLOW):
     *  - Bước 1: React gửi các tham số startDate, endDate, groupBy trên URL query string (@RequestParam).
     *  - Bước 2: Controller kiểm tra nếu thiếu ngày -> Gán ngày mặc định (30 ngày gần nhất).
     *  - Bước 3: Controller kiểm tra chuỗi groupBy (hour, day, month, year) và ép kiểu sang Enum GroupByMode.
     *  - Bước 4: Controller gọi reportService.getRevenueStatistics() viết SQL tổng hợp doanh thu trong MySQL.
     *  - Bước 5: Đóng gói và trả về JSON chứa mảng chartData cho React vẽ biểu đồ.
     */
    @GetMapping("/revenue")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<?> getRevenueStatistics(
            @RequestParam(value = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(value = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(value = "groupBy", defaultValue = "day") String groupBy) {

        try {
            // Bước A: Thiết lập giá trị mặc định nếu tham số bị bỏ trống
            if (startDate == null) {
                startDate = LocalDate.now().minusDays(30);
            }
            if (endDate == null) {
                endDate = LocalDate.now();
            }

            // Bước B: Chuyển đổi chuỗi groupBy sang enum GroupByMode tương ứng
            GroupByMode mode;
            try {
                mode = GroupByMode.valueOf(groupBy.toUpperCase());
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body(Map.of("message", "Chế độ nhóm 'groupBy' chỉ chấp nhận: hour, day, month hoặc year"));
            }

            // Bước C: Gọi service xử lý nghiệp vụ truy vấn MySQL
            RevenueStatsResponse stats = reportService.getRevenueStatistics(startDate, endDate, mode);
            return ResponseEntity.ok(stats);

        } catch (IllegalArgumentException e) {
            // Bắt các lỗi validate khoảng ngày hoặc hạn mức dung lượng
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            // Phòng tránh lỗi runtime không lường trước
            return ResponseEntity.internalServerError().body(Map.of("message", "Lỗi xử lý báo cáo thống kê: " + e.getMessage()));
        }
    }
}
