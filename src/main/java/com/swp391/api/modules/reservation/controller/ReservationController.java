package com.swp391.api.modules.reservation.controller;

import com.swp391.api.modules.reservation.dto.CreateReservationRequest;
import com.swp391.api.modules.reservation.dto.ReservationResponse;
import com.swp391.api.modules.reservation.service.ReservationService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ====================================================================================
 * BẢN HƯỚNG DẪN CHI TIẾT SPRING BOOT REST CONTROLLER (MODULE ĐẶT BÀN - RESERVATIONS)
 * ====================================================================================
 * VAI TRÒ CỦA CONTROLLER:
 *  - Đóng vai trò là LỄ TÂN NHẬN YÊU CẦU từ Frontend (React).
 *  - Lắng nghe các yêu cầu HTTP (GET, POST, PATCH) tại địa chỉ /api/reservations.
 *  - Kiểm tra tính hợp lệ dữ liệu (@Valid), sau đó gọi Service xử lý nghiệp vụ lưu vào MySQL.
 *  - Trả kết quả JSON kèm mã trạng thái HTTP thích hợp (200 OK, 201 Created) cho React.
 * ====================================================================================
 */
@RestController
@RequestMapping("/api/reservations")
public class ReservationController {

    private final ReservationService reservationService;

    // Dependency Injection: Khởi tạo Service qua Constructor
    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    /**
     * API 1: Khách hàng tạo đơn đặt bàn Online
     * - URL: POST http://localhost:8080/api/reservations
     * 
     * QUY TRÌNH THỰC THI (STEP-BY-STEP FLOW):
     *  - Bước 1 (React FE): Khách điền Form đặt bàn trên giao diện -> Gọi POST /api/reservations.
     *  - Bước 2 (Controller): @RequestBody nhận JSON gửi lên bóc tách thành đối tượng CreateReservationRequest.
     *  - Bước 3 (Validation): @Valid tự động kiểm tra xem Tên, SĐT, Ngày/Giờ có bị trống hay sai định dạng không.
     *  - Bước 4 (Service): Gọi reservationService.createReservation() lưu vào MySQL với trạng thái PENDING.
     *  - Bước 5 (Response): Trả về HTTP Status 201 CREATED kèm thông tin đơn đặt bàn JSON vừa tạo.
     */
    @PostMapping
    public ResponseEntity<ReservationResponse> createReservation(@Valid @RequestBody CreateReservationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reservationService.createReservation(request));
    }

    /**
     * API 2: Lễ tân tạo đơn đặt bàn tại chỗ (Walk-in) cho khách đến trực tiếp
     * - URL: POST http://localhost:8080/api/reservations/walk-in
     * 
     * QUY TRÌNH THỰC THI (STEP-BY-STEP FLOW):
     *  - Bước 1: Lễ tân thao tác tạo đơn Walk-in trên giao diện.
     *  - Bước 2: Controller nhận dữ liệu và gọi reservationService.createWalkInReservation(request).
     *  - Bước 3: Đơn hàng được tạo ngay ở trạng thái CONFIRMED và gán bàn khả dụng.
     *  - Bước 4: Trả về HTTP Status 201 CREATED cho Frontend.
     */
    @PostMapping("/walk-in")
    public ResponseEntity<ReservationResponse> createWalkInReservation(@Valid @RequestBody CreateReservationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reservationService.createWalkInReservation(request));
    }

    /**
     * API 3: Khách hàng xem lịch sử các đơn đặt bàn của chính mình
     * - URL: GET http://localhost:8080/api/reservations/me
     * 
     * QUY TRÌNH THỰC THI (STEP-BY-STEP FLOW):
     *  - Bước 1: React gửi HTTP GET kèm JWT Token xác thực của khách hàng.
     *  - Bước 2: Controller gọi reservationService.getMyReservations() lấy danh sách từ MySQL dựa trên email/userId trong Token.
     *  - Bước 3: Trả về danh sách JSON chứa các đơn đặt bàn (HTTP 200 OK).
     */
    @GetMapping("/me")
    public ResponseEntity<List<ReservationResponse>> getMyReservations() {
        return ResponseEntity.ok(reservationService.getMyReservations());
    }

    /**
     * API 4: Admin / Lễ tân xem toàn bộ danh sách đặt bàn trong hệ thống
     * - URL: GET http://localhost:8080/api/reservations
     */
    @GetMapping
    public ResponseEntity<List<ReservationResponse>> getAllReservations() {
        return ResponseEntity.ok(reservationService.getAllReservations());
    }

    /**
     * API 5: Hủy đơn đặt bàn (Bởi Khách hàng hoặc Lễ tân)
     * - URL: PATCH http://localhost:8080/api/reservations/{id}/cancel
     * 
     * QUY TRÌNH THỰC THI (STEP-BY-STEP FLOW):
     *  - Bước 1: React gửi lệnh PATCH kèm {id} của đơn đặt bàn trên thanh URL (@PathVariable).
     *  - Bước 2: Controller chuyển id sang reservationService.cancelReservation(reservationId).
     *  - Bước 3: Service đổi trạng thái trong MySQL thành CANCELLED và giải phóng bàn ăn (nếu có).
     *  - Bước 4: Trả về thông tin đơn đã hủy (HTTP 200 OK).
     */
    @PatchMapping("/{id}/cancel")
    public ResponseEntity<ReservationResponse> cancelReservation(@PathVariable("id") Long reservationId) {
        return ResponseEntity.ok(reservationService.cancelReservation(reservationId));
    }

    /**
     * API 6: Lễ tân / Quản lý duyệt xác nhận đơn đặt bàn
     * - URL: PATCH http://localhost:8080/api/reservations/{id}/confirm
     */
    @PatchMapping("/{id}/confirm")
    public ResponseEntity<ReservationResponse> confirmReservation(@PathVariable("id") Long reservationId) {
        return ResponseEntity.ok(reservationService.confirmReservation(reservationId));
    }

    /**
     * API 7: Lễ tân gán bàn trống cho đơn đặt bàn đã xác nhận
     * - URL: PATCH http://localhost:8080/api/reservations/{id}/assign-tables
     */
    @PatchMapping("/{id}/assign-tables")
    public ResponseEntity<ReservationResponse> assignTables(
            @PathVariable("id") Long reservationId,
            @Valid @RequestBody com.swp391.api.modules.reservation.dto.AssignTablesRequest request) {
        return ResponseEntity.ok(reservationService.assignTables(reservationId, request));
    }

    /**
     * API 8: Lễ tân đổi bàn cho khách
     * - URL: PATCH http://localhost:8080/api/reservations/{id}/change-tables
     */
    @PatchMapping("/{id}/change-tables")
    public ResponseEntity<ReservationResponse> changeTables(
            @PathVariable("id") Long reservationId,
            @Valid @RequestBody com.swp391.api.modules.reservation.dto.AssignTablesRequest request) {
        return ResponseEntity.ok(reservationService.changeTables(reservationId, request));
    }
}
