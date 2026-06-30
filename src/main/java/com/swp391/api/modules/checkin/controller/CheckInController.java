package com.swp391.api.modules.checkin.controller;

import com.swp391.api.modules.checkin.dto.CheckInRequest;
import com.swp391.api.modules.checkin.service.CheckInService;
import com.swp391.api.modules.reservation.dto.ReservationResponse;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/check-in")
@CrossOrigin(origins = "http://localhost:5173", allowedHeaders = "*", methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.OPTIONS})
public class CheckInController {

    private final CheckInService checkInService;

    public CheckInController(CheckInService checkInService) {
        this.checkInService = checkInService;
    }

    /**
     * Lấy danh sách khách chờ check-in theo ngày
     * GET /api/check-in/reservations?date=2026-06-25
     */
    @GetMapping("/reservations")
    public ResponseEntity<List<ReservationResponse>> getCheckInReservations(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(checkInService.getCheckInReservations(date, search));
    }

    /**
     * Thực hiện gán bàn và check-in
     * POST /api/check-in/assign
     */
    @PostMapping("/assign")
    public ResponseEntity<ReservationResponse> processCheckIn(@Valid @RequestBody CheckInRequest request) {
        return ResponseEntity.ok(checkInService.processCheckInTransaction(request));
    }
    /**
     * Lấy thông tin khách hàng thực tế đang ngồi tại bàn OCCUPIED
     * GET /api/check-in/table/{tableId}/active-guest
     */
    @GetMapping("/table/{tableId}/active-guest")
    public ResponseEntity<com.swp391.api.modules.checkin.dto.ActiveGuestResponse> getActiveGuestByTable(
            @PathVariable Long tableId) {
        return ResponseEntity.ok(checkInService.getActiveGuestByTable(tableId));
    }

    @GetMapping("/table/{tableId}/reserved-guest")
    public ResponseEntity<com.swp391.api.modules.checkin.dto.ActiveGuestResponse> getReservedGuestByTable(
            @PathVariable Long tableId) {
        return ResponseEntity.ok(checkInService.getReservedGuestByTable(tableId));
    }
}
