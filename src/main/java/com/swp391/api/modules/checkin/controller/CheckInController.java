package com.swp391.api.modules.checkin.controller;

import com.swp391.api.modules.checkin.dto.CheckInRequest;
import com.swp391.api.modules.checkin.dto.ActiveGuestResponse;
import com.swp391.api.modules.checkin.service.CheckInService;
import com.swp391.api.modules.reservation.dto.ReservationResponse;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * ====================================================================================
 * BẢN HƯỚNG DẪN CHI TIẾT SPRING BOOT REST CONTROLLER (MODULE CHECK-IN BÀN ĂN)
 * ====================================================================================
 * CÁCH SPRING BOOT KẾT NỐI VỚI REACT FRONTEND:
 * 
 * 1. @RestController: Đánh dấu Class này chứa các API RESTful. Mọi dữ liệu hàm trả về 
 *    sẽ tự động được Spring Boot chuyển đổi (Serialize) thành định dạng JSON để React đọc.
 * 2. @RequestMapping("/api/check-in"): Khai báo tiền tố URL chung cho tất cả API trong controller.
 * 3. @CrossOrigin: Cho phép trình duyệt từ địa chỉ Frontend (localhost:5173 / 5174) 
 *    truy cập và lấy dữ liệu mà không bị chặn bởi chính sách bảo mật CORS của trình duyệt.
 * ====================================================================================
 */
@RestController
@RequestMapping("/api/check-in")
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:5174"}, allowedHeaders = "*", methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.OPTIONS})
public class CheckInController {

    private final CheckInService checkInService;

    // Dependency Injection: Khởi tạo Service qua Constructor
    public CheckInController(CheckInService checkInService) {
        this.checkInService = checkInService;
    }

    /**
     * API 1: Lấy danh sách khách chờ check-in theo ngày
     * - URL: GET http://localhost:8080/api/check-in/reservations?date=2026-08-02
     * 
     * BƯỚC THỰC THI (STEP-BY-STEP FLOW):
     *  - Bước 1: React gọi API mang theo ngày (@RequestParam date) và từ khóa tìm kiếm (search).
     *  - Bước 2: Controller nhận dữ liệu và đẩy xuống Service (checkInService.getCheckInReservations).
     *  - Bước 3: Service dùng JPA truy vấn MySQL lấy danh sách các đơn đặt bàn có trạng thái CONFIRMED.
     *  - Bước 4: Trả về HTTP Status 200 OK kèm danh sách JSON đối tượng ReservationResponse.
     */
    @GetMapping("/reservations")
    public ResponseEntity<List<ReservationResponse>> getCheckInReservations(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(checkInService.getCheckInReservations(date, search));
    }

    /**
     * API 2: Thực hiện gán bàn và Check-in đón khách tại nhà hàng
     * - URL: POST http://localhost:8080/api/check-in/assign
     * 
     * BƯỚC THỰC THI (STEP-BY-STEP FLOW):
     *  - Bước 1: React gửi dữ liệu JSON (reservationId, list bàn ăn được chọn) qua @RequestBody.
     *  - Bước 2: Annotation @Valid kiểm tra xem dữ liệu gửi lên có bị trống hay vi phạm điều kiện không.
     *  - Bước 3: Controller gọi Service thực hiện Transaction (gán bàn, đổi trạng thái bàn thành OCCUPIED, mở đơn hàng).
     *  - Bước 4: Trả về HTTP 200 OK cùng thông tin đơn check-in hoàn tất.
     */
    @PostMapping("/assign")
    public ResponseEntity<ReservationResponse> processCheckIn(@Valid @RequestBody CheckInRequest request) {
        return ResponseEntity.ok(checkInService.processCheckInTransaction(request));
    }

    /**
     * API 3: Lấy thông tin khách hàng thực tế đang ngồi tại bàn (Trạng thái OCCUPIED)
     * - URL: GET http://localhost:8080/api/check-in/table/{tableId}/active-guest
     */
    @GetMapping("/table/{tableId}/active-guest")
    public ResponseEntity<ActiveGuestResponse> getActiveGuestByTable(
            @PathVariable Long tableId) {
        ActiveGuestResponse guest = checkInService.getActiveGuestByTable(tableId);
        return guest == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(guest);
    }

    /**
     * API 4: Lấy thông tin khách hàng đã đặt giữ chỗ trước tại bàn (Trạng thái RESERVED)
     * - URL: GET http://localhost:8080/api/check-in/table/{tableId}/reserved-guest
     */
    @GetMapping("/table/{tableId}/reserved-guest")
    public ResponseEntity<ActiveGuestResponse> getReservedGuestByTable(
            @PathVariable Long tableId) {
        ActiveGuestResponse guest = checkInService.getReservedGuestByTable(tableId);
        return guest == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(guest);
    }
}
